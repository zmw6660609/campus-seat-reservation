package com.sr;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.sr.mapper")
@EnableScheduling
public class SrServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SrServerApplication.class, args);
    }
}
