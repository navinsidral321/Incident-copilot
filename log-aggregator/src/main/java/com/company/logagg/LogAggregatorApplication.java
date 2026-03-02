package com.company.logagg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class LogAggregatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogAggregatorApplication.class, args);
    }
}
