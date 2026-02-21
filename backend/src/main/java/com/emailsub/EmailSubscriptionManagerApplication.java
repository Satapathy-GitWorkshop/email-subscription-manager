package com.emailsub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmailSubscriptionManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmailSubscriptionManagerApplication.class, args);
    }
}
