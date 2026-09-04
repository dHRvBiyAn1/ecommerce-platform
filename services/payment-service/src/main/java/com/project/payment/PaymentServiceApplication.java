package com.project.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableMongoAuditing
@EnableFeignClients(basePackages = "com.project.payment.client")
@SpringBootApplication(scanBasePackages = {"com.project.payment", "com.project.common"})
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
