package com.example.appbackend;

import com.example.appbackend.config.LocalDotEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Paths;

@SpringBootApplication
@EnableScheduling
public class AppBackendApplication {

    public static void main(String[] args) {
        LocalDotEnvLoader.loadForWorkingDirectory(Paths.get("").toAbsolutePath());
        SpringApplication.run(AppBackendApplication.class, args);
        System.out.println("SUCCESS！！！");
    }

}
