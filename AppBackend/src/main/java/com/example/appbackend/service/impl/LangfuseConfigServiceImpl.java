package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LangfuseConfigDTO;
import com.example.appbackend.entity.LangfuseConfig;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.LangfuseConfigRepository;
import com.example.appbackend.service.LangfuseConfigService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.format.DateTimeFormatter;

@Service
public class LangfuseConfigServiceImpl implements LangfuseConfigService {

    private static final long CONFIG_ID = 1L;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LangfuseConfigRepository repository;
    private final WebClient.Builder webClientBuilder;

    public LangfuseConfigServiceImpl(LangfuseConfigRepository repository, WebClient.Builder webClientBuilder) {
        this.repository = repository;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public LangfuseConfigDTO.ConfigVO getConfig() {
        return toVO(load());
    }

    @Override
    public LangfuseConfigDTO.ConfigVO updateConfig(LangfuseConfigDTO.UpdateRequest request) {
        LangfuseConfig config = load();
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        if (StringUtils.hasText(request.getBaseUrl())) {
            config.setBaseUrl(request.getBaseUrl().trim().replaceAll("/+$", ""));
        }
        if (StringUtils.hasText(request.getPublicKey())) {
            config.setPublicKey(request.getPublicKey().trim());
        }
        if (StringUtils.hasText(request.getSecretKey())) {
            config.setSecretKey(request.getSecretKey().trim());
        }
        if (Boolean.TRUE.equals(config.getEnabled()) && (!StringUtils.hasText(config.getBaseUrl())
                || !StringUtils.hasText(config.getPublicKey()) || !StringUtils.hasText(config.getSecretKey()))) {
            throw new BusinessException(400, "启用 Langfuse 前请填写服务地址、Public Key 和 Secret Key");
        }
        return toVO(repository.save(config));
    }

    @Override
    public void applyPythonHeaders(HttpHeaders headers) {
        LangfuseConfig config = load();
        headers.set("X-Langfuse-Enabled", String.valueOf(Boolean.TRUE.equals(config.getEnabled())));
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        headers.set("X-Langfuse-Base-Url", config.getBaseUrl());
        headers.set("X-Langfuse-Public-Key", config.getPublicKey());
        headers.set("X-Langfuse-Secret-Key", config.getSecretKey());
    }

    @Override
    public LangfuseConfigDTO.TestResultVO testConfig() {
        LangfuseConfig config = load();
        LangfuseConfigDTO.TestResultVO result = new LangfuseConfigDTO.TestResultVO();
        String target = (StringUtils.hasText(config.getBaseUrl()) ? config.getBaseUrl().replaceAll("/+$", "") : "") + "/api/public/projects";
        result.setTarget(target);
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            result.setSuccess(false);
            result.setDetail("Langfuse 当前未启用，请先开启并保存配置。");
            return result;
        }
        if (!StringUtils.hasText(config.getBaseUrl()) || !StringUtils.hasText(config.getPublicKey()) || !StringUtils.hasText(config.getSecretKey())) {
            result.setSuccess(false);
            result.setDetail("数据库中的 Langfuse 服务地址或密钥不完整。");
            return result;
        }
        try {
            webClientBuilder.build()
                    .get()
                    .uri(target)
                    .headers(headers -> headers.setBasicAuth(config.getPublicKey(), config.getSecretKey()))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            result.setSuccess(true);
            result.setDetail("数据库中的 Langfuse 配置认证成功。");
        } catch (Exception error) {
            result.setSuccess(false);
            Integer status = extractStatus(error);
            result.setDetail(status != null && status == 401
                    ? "Langfuse 返回 401：数据库保存的 Public Key / Secret Key 与该服务地址不匹配。"
                    : "连接 Langfuse 失败：" + safeMessage(error));
        }
        return result;
    }

    private LangfuseConfig load() {
        return repository.findById(CONFIG_ID).orElseGet(() -> {
            LangfuseConfig config = new LangfuseConfig();
            config.setId(CONFIG_ID);
            return config;
        });
    }

    private LangfuseConfigDTO.ConfigVO toVO(LangfuseConfig config) {
        LangfuseConfigDTO.ConfigVO vo = new LangfuseConfigDTO.ConfigVO();
        vo.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        vo.setBaseUrl(config.getBaseUrl());
        vo.setPublicKeyConfigured(StringUtils.hasText(config.getPublicKey()));
        vo.setSecretKeyConfigured(StringUtils.hasText(config.getSecretKey()));
        vo.setPublicKeyMasked(mask(config.getPublicKey()));
        vo.setSecretKeyMasked(mask(config.getSecretKey()));
        vo.setUpdateTime(config.getUpdateTime() == null ? null : config.getUpdateTime().format(TIME_FORMAT));
        return vo;
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) return "";
        String trimmed = value.trim();
        return trimmed.length() <= 8 ? "已配置" : trimmed.substring(0, 4) + "••••" + trimmed.substring(trimmed.length() - 4);
    }

    private Integer extractStatus(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof org.springframework.web.reactive.function.client.WebClientResponseException exception) {
                return exception.getStatusCode().value();
            }
            current = current.getCause();
        }
        return null;
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return StringUtils.hasText(message) ? message.replaceAll("(?i)(pk-lf|sk-lf)-[a-z0-9-]+", "$1-***") : "未知错误";
    }
}
