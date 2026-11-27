package study.locker.edge.adapter.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import study.locker.edge.application.GatewayFailure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Consumer;

public final class CallerQuota implements AutoCloseable {
    @ConfigurationProperties("locker.quota")
    public record Settings(
            @DefaultValue("redis://valkey:6379") String uri,
            @DefaultValue("100ms") Duration timeout,
            @DefaultValue("120") int rate,
            @DefaultValue("60") int burst,
            @DefaultValue("20") int localRate,
            @DefaultValue("10") int localBurst,
            @DefaultValue("10000") int cacheSize) {
        public Settings {
            if (!timeout.isPositive()
                    || rate < 1
                    || burst < 1
                    || localRate < 1
                    || localBurst < 1
                    || cacheSize < 1) {
                throw new IllegalArgumentException("Quota budgets must be positive");
            }
        }
    }

    private final String keyPrefix;
    private final ValkeyQuotaStore shared;
    private final Bandwidth localBudget;
    private final Cache<String, Bucket> localBuckets;
    private final Consumer<String> events;

    public CallerQuota(Settings settings, Consumer<String> events) {
        this.events = events;
        keyPrefix = "locker:quota:" + settings.rate() + ":" + settings.burst() + ":";
        shared =
                new ValkeyQuotaStore(
                        settings.uri(),
                        settings.timeout(),
                        perMinute(settings.rate(), settings.burst()));
        localBudget = perMinute(settings.localRate(), settings.localBurst());
        localBuckets =
                Caffeine.newBuilder()
                        .maximumSize(settings.cacheSize())
                        .expireAfterAccess(Duration.ofMinutes(10))
                        .build();
    }

    public void charge(String issuer, String subject) {
        String key = keyPrefix + identityHash(issuer, subject);
        ConsumptionProbe decision;
        try {
            decision = shared.tryConsume(key).orElseGet(() -> consumeLocally(key));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GatewayFailure("UNAVAILABLE", "Quota check interrupted");
        }
        if (!decision.isConsumed()) {
            events.accept("rejected");
            long waitSeconds =
                    Math.ceilDiv(
                            decision.getNanosToWaitForRefill(), Duration.ofSeconds(1).toNanos());
            throw new GatewayFailure("RESOURCE_EXHAUSTED", "Caller quota exceeded", waitSeconds);
        }
    }

    private ConsumptionProbe consumeLocally(String key) {
        events.accept("fallback");
        var bucket =
                localBuckets.get(key, ignored -> Bucket.builder().addLimit(localBudget).build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private static Bandwidth perMinute(int rate, int burst) {
        return Bandwidth.builder()
                .capacity(burst)
                .refillGreedy(rate, Duration.ofMinutes(1))
                .build();
    }

    private static String identityHash(String issuer, String subject) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(issuer.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of()
                    .formatHex(digest.digest(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    public void close() {
        shared.close();
    }
}
