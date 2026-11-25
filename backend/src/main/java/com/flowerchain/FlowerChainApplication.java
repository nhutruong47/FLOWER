package com.flowerchain;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FlowerChainApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowerChainApplication.class, args);
    }

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}
