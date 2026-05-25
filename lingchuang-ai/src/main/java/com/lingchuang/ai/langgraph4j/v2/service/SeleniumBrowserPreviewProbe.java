package com.lingchuang.ai.langgraph4j.v2.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.langgraph4j.v2.model.BrowserVerificationResult;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Selenium 浏览器探测器。
 */
@Component
@Slf4j
public class SeleniumBrowserPreviewProbe implements BrowserPreviewProbe {

    private static final int DEFAULT_WIDTH = 1365;
    private static final int DEFAULT_HEIGHT = 768;

    @Override
    public BrowserVerificationResult probe(String previewUrl, String screenshotPath) {
        if (StrUtil.isBlank(previewUrl)) {
            return BrowserVerificationResult.builder()
                    .enabled(true)
                    .passed(false)
                    .summary("浏览器验证失败")
                    .errorMessage("预览地址为空")
                    .failureType("missing_preview_url")
                    .build();
        }
        WebDriver driver = null;
        try {
            driver = createDriver();
            driver.get(previewUrl);
            waitForPageLoad(driver);
            int firstScreenTextLength = readVisibleTextLength(driver);
            List<String> consoleErrors = readConsoleErrors(driver);
            saveScreenshot(driver, screenshotPath);
            List<String> issues = new ArrayList<>();
            if (firstScreenTextLength <= 0) {
                issues.add("浏览器首屏内容为空");
            }
            if (!consoleErrors.isEmpty()) {
                issues.add("浏览器 console error: " + String.join("；", consoleErrors));
            }
            boolean passed = issues.isEmpty();
            return BrowserVerificationResult.builder()
                    .enabled(true)
                    .passed(passed)
                    .previewUrl(previewUrl)
                    .screenshotPath(screenshotPath)
                    .firstScreenTextLength(firstScreenTextLength)
                    .consoleErrors(consoleErrors)
                    .issues(issues)
                    .summary(passed ? "浏览器验证通过" : "浏览器验证失败，发现 %d 个问题".formatted(issues.size()))
                    .errorMessage(passed ? null : String.join("；", issues))
                    .failureType(passed ? null : determineFailureType(firstScreenTextLength, consoleErrors))
                    .build();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private WebDriver createDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-extensions");
        options.addArguments("--window-size=%d,%d".formatted(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));
        return driver;
    }

    private void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(currentDriver -> "complete".equals(((JavascriptExecutor) currentDriver)
                    .executeScript("return document.readyState")));
            Thread.sleep(500);
        } catch (Exception e) {
            log.warn("浏览器等待页面加载异常，继续验证: {}", e.getMessage());
        }
    }

    private int readVisibleTextLength(WebDriver driver) {
        Object text = ((JavascriptExecutor) driver).executeScript("return (document.body && document.body.innerText || '').trim();");
        return text == null ? 0 : text.toString().replaceAll("\\s+", "").length();
    }

    private List<String> readConsoleErrors(WebDriver driver) {
        try {
            LogEntries entries = driver.manage().logs().get(LogType.BROWSER);
            List<String> consoleErrors = new ArrayList<>();
            for (LogEntry entry : entries) {
                if (entry.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    consoleErrors.add(entry.getLevel() + " " + entry.getMessage());
                }
            }
            return consoleErrors;
        } catch (Exception e) {
            log.warn("读取浏览器 console 日志失败: {}", e.getMessage());
            return List.of();
        }
    }

    private void saveScreenshot(WebDriver driver, String screenshotPath) {
        if (StrUtil.isBlank(screenshotPath)) {
            return;
        }
        byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        FileUtil.writeBytes(screenshotBytes, screenshotPath);
    }

    private String determineFailureType(int firstScreenTextLength, List<String> consoleErrors) {
        if (firstScreenTextLength <= 0) {
            return "first_screen_empty";
        }
        if (!consoleErrors.isEmpty()) {
            return "console_error";
        }
        return "browser_check";
    }
}
