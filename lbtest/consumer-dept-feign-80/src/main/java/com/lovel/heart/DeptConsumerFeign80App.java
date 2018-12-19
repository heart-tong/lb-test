package com.lovel.heart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;

@SpringBootApplication
@EnableEurekaClient
@EnableFeignClients(basePackages= {"com.lovel.heart"})
public class DeptConsumerFeign80App {

    public static void main(String[] args) {
        SpringApplication.run(DeptConsumerFeign80App.class, args);
    }
}
