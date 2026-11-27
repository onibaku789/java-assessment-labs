package study.locker.edge.adapter.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

final class ValkeyQuotaStore implements AutoCloseable {
    private final RedisURI uri;
    private final Duration timeout;
    private final RedisClient client;
    private final BucketConfiguration budget;
    private CompletableFuture<AsyncProxyManager<byte[]>> connection;
    private long nextConnectNanos;

    ValkeyQuotaStore(String address, Duration timeout, Bandwidth limit) {
        this.timeout = timeout;
        budget = BucketConfiguration.builder().addLimit(limit).build();
        uri = RedisURI.create(address);
        uri.setTimeout(timeout);
        client = RedisClient.create(uri);
        client.setOptions(
                ClientOptions.builder()
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .socketOptions(SocketOptions.builder().connectTimeout(timeout).build())
                        .timeoutOptions(TimeoutOptions.enabled(timeout))
                        .build());
        connect();
    }

    /**
     * An empty result means the store was unavailable. A denied probe is a valid shared decision.
     */
    Optional<ConsumptionProbe> tryConsume(String key) throws InterruptedException {
        byte[] bucketKey = key.getBytes(StandardCharsets.UTF_8);
        try {
            var check =
                    connect()
                            .thenCompose(
                                    store ->
                                            store.builder()
                                                    .build(bucketKey, budget)
                                                    .tryConsumeAndReturnRemaining(1));
            return Optional.of(check.orTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS).get());
        } catch (ExecutionException | RedisException unavailable) {
            return Optional.empty();
        }
    }

    private synchronized CompletableFuture<AsyncProxyManager<byte[]>> connect() {
        // Lettuce reconnects an established connection. Only a failed initial connection needs
        // replacing.
        if (connection == null
                || (connection.isCompletedExceptionally()
                        && System.nanoTime() - nextConnectNanos >= 0)) {
            nextConnectNanos = System.nanoTime() + timeout.toNanos();
            connection =
                    client.connectAsync(ByteArrayCodec.INSTANCE, uri)
                            .thenApply(this::bucketManager)
                            .toCompletableFuture();
        }
        return connection;
    }

    private AsyncProxyManager<byte[]> bucketManager(
            StatefulRedisConnection<byte[], byte[]> connection) {
        var expiry =
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                        Duration.ofMinutes(1));
        return Bucket4jLettuce.casBasedBuilder(connection)
                .requestTimeout(timeout)
                .expirationAfterWrite(expiry)
                .build()
                .asAsync();
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
