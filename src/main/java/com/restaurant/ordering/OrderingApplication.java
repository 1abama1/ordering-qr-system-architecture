package com.restaurant.ordering;
import com.restaurant.ordering.common.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
public class OrderingApplication {
    public static void main(String[] args) { SpringApplication.run(OrderingApplication.class, args); }
}
