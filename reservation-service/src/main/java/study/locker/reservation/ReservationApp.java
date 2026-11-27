package study.locker.reservation;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReservationApp {
    public static void main(String[] args) {
        SpringApplication.run(ReservationApp.class, args);
    }
}
