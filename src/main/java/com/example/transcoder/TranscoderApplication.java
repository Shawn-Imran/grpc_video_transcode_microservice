package com.example.transcoder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TranscoderApplication {

    public static void main(String[] args) {
        SpringApplication.run(TranscoderApplication.class, args);
    }
}

