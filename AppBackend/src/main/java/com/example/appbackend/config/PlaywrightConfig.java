package com.example.appbackend.config;

import com.microsoft.playwright.Playwright;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class PlaywrightConfig {

    /**
     * Playwright 单例
     * Playwright 实例是线程安全的，应该全局复用
     */
    @Bean
    @Scope("singleton")
    public Playwright playwright() {
        return Playwright.create();
    }
}