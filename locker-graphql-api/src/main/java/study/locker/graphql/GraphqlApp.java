package study.locker.graphql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import study.locker.edge.bootstrap.EdgeConfig;

@SpringBootApplication
@Import(EdgeConfig.class)
public class GraphqlApp {
    public static void main(String[] args) {
        SpringApplication.run(GraphqlApp.class, args);
    }
}
