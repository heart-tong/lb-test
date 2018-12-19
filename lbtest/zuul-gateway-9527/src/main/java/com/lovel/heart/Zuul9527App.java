package com.lovel.heart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;

@SpringBootApplication
@EnableZuulProxy
public class Zuul9527App {
    public static void main(String[] args) {
        SpringApplication.run(Zuul9527App.class, args);
    }
}
