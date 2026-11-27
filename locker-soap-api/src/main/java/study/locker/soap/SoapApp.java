package study.locker.soap;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.*;

import study.locker.edge.bootstrap.EdgeConfig;

@SpringBootApplication
@Import(EdgeConfig.class)
public class SoapApp {
    public static void main(String[] args) {
        SpringApplication.run(SoapApp.class, args);
    }
}
