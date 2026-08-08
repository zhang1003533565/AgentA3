package com.example.appbackend.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.List;

@Lazy
@Service
public class PlaywrightService {

    // 使用 ObjectProvider 延迟获取 Playwright，避免在启动阶段触发 Playwright.create()
    // （该方法会下载/安装浏览器驱动，可能长时间阻塞应用启动）
    private final ObjectProvider<Playwright> playwrightProvider;

    public PlaywrightService(ObjectProvider<Playwright> playwrightProvider) {
        this.playwrightProvider = playwrightProvider;
    }

    /**
     * 创建浏览器上下文（每次任务调用）
     */
    public BrowserContext createBrowserContext(boolean headless) {
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
            .setHeadless(headless)
            .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage"));

        Browser browser = playwrightProvider.getObject().chromium().launch(launchOptions);
        return browser.newContext();
    }

    /**
     * 访问网页
     */
    public Page navigate(BrowserContext context, String url) {
        Page page = context.newPage();
        page.navigate(url);
        return page;
    }

    /**
     * 等待页面加载完成
     */
    public void waitForLoadState(Page page, LoadState state) {
        page.waitForLoadState(state);
    }

    /**
     * 等待元素出现
     */
    public Locator waitForSelector(Page page, String selector) {
        return page.locator(selector);
    }

    /**
     * 点击元素
     */
    public void click(Page page, String selector) {
        page.locator(selector).click();
    }

    /**
     * 输入文本
     */
    public void fill(Page page, String selector, String text) {
        page.locator(selector).fill(text);
    }

    /**
     * 获取页面标题
     */
    public String getTitle(Page page) {
        return page.title();
    }

    /**
     * 获取当前 URL
     */
    public String getCurrentUrl(Page page) {
        return page.url();
    }

    /**
     * 获取页面内容 (HTML)
     */
    public String getContent(Page page) {
        return page.content();
    }

    /**
     * 执行 JavaScript
     */
    public Object evaluate(Page page, String script) {
        return page.evaluate(script);
    }

    /**
     * 执行 JavaScript 并返回复杂结果
     */
    public JSHandle evaluateHandle(Page page, String script) {
        return page.evaluateHandle(script);
    }

    /**
     * 等待并获取元素文本
     */
    public String getTextContent(Page page, String selector) {
        return page.locator(selector).textContent();
    }

    /**
     * 关闭上下文
     */
    public void closeContext(BrowserContext context) {
        if (context != null) {
            context.close();
        }
    }

    /**
     * 关闭浏览器
     */
    public void closeBrowser(BrowserContext context) {
        if (context != null) {
            Browser browser = context.browser();
            if (browser != null) {
                browser.close();
            }
        }
    }
}