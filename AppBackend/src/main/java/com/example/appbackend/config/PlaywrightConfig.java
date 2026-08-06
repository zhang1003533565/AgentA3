package com.example.appbackend.config;

import com.microsoft.playwright.Playwright;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

@Configuration
public class PlaywrightConfig {

    /**
     * Playwright 单例
     * Playwright 实例是线程安全的，应该全局复用
     * 使用 @Lazy 延迟初始化，避免启动时启动 Driver 子进程阻塞应用启动
     */
    @Bean
    @Scope("singleton")
    @Lazy
    public Playwright playwright() {
        return Playwright.create();
    }
}