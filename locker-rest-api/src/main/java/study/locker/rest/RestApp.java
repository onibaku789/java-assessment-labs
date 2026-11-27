package study.locker.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import study.locker.edge.bootstrap.EdgeConfig;

@SpringBootApplication
@Import(EdgeConfig.class)
public class RestApp {
    public static void main(String[] args) {
        SpringApplication.run(RestApp.class, args);
    }
}
