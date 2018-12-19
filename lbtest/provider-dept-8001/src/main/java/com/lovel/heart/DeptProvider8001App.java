package com.lovel.heart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class DeptProvider8001App {

    public static void main(String[] args) {
        SpringApplication.run(DeptProvider8001App.class, args);
    }
}
