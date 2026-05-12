package com.ai.app;

import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = MybatisAutoConfiguration.class)
@MapperScan("com.ai.app.chat.mapper")
public class MyAiAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyAiAppApplication.class, args);
    }

}