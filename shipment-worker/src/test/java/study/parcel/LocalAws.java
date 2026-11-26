package study.parcel;

import java.net.URI;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

final class LocalAws {
    private static final StaticCredentialsProvider LOCAL =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"));

    static GenericContainer<?> objectsContainer() {
        return new GenericContainer<>("adobe/s3mock:5.2.3").withExposedPorts(9090)
                .waitingFor(Wait.forHttp("/").forPort(9090));
    }

    static GenericContainer<?> queueContainer() {
        return new GenericContainer<>("softwaremill/elasticmq-native:1.7.1").withExposedPorts(9324)
                .withCopyToContainer(Transferable.of("include classpath(\"application.conf\")\nnode-address.host=\"*\"\n"),
                        "/opt/elasticmq.conf");
    }

    static S3Client objects(GenericContainer<?> container) {
        return S3Client.builder().credentialsProvider(LOCAL).region(Region.US_EAST_1).forcePathStyle(true)
                .endpointOverride(endpoint(container, 9090)).build();
    }

    static SqsClient queue(GenericContainer<?> container) {
        return SqsClient.builder().credentialsProvider(LOCAL).region(Region.US_EAST_1)
                .endpointOverride(endpoint(container, 9324)).build();
    }

    private static URI endpoint(GenericContainer<?> container, int port) {
        return URI.create("http://" + container.getHost() + ":" + container.getMappedPort(port));
    }
}
