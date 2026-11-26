package study.parcel;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
public class ReleaseLab {
    record Reply(String replica, String region) {}

    @Value("${parcel.region:local}") private String region;

    public static void main(String[] args) {
        SpringApplication.run(ReleaseLab.class, args);
    }

    @GetMapping("/track")
    Reply track() {
        return new Reply(System.getenv().getOrDefault("HOSTNAME", "local"), region);
    }

    @GetMapping("/work")
    Reply work() {
        var clock = ManagementFactory.getThreadMXBean();
        long until = clock.getCurrentThreadCpuTime() + 12_000_000L;
        while (clock.getCurrentThreadCpuTime() < until) Thread.onSpinWait();
        return track();
    }

    @Bean
    HealthIndicator gate(@Value("${parcel.accepting:true}") boolean accepting) {
        return () -> accepting && !Files.exists(Path.of("/tmp/parcel-study-paused"))
                ? Health.up().build() : Health.down().build();
    }
}
