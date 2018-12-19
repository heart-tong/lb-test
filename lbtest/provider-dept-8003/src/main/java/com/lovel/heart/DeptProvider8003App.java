package com.lovel.heart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class DeptProvider8003App {

    public static void main(String[] args) {
        SpringApplication.run(DeptProvider8003App.class, args);
    }
}
