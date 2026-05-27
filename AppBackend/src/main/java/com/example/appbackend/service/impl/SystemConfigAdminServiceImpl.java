package com.example.appbackend.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.SystemConfigDTO;
import com.example.appbackend.entity.SystemConfig;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.SystemConfigAdminService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Service
public class SystemConfigAdminServiceImpl implements SystemConfigAdminService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SystemConfigRepository systemConfigRepository;
    private final WebClient tencentMapWebClient;

    public SystemConfigAdminServiceImpl(SystemConfigRepository systemConfigRepository, WebClient tencentMapWebClient) {
        this.systemConfigRepository = systemConfigRepository;
        this.tencentMapWebClient = tencentMapWebClient;
    }

    @Override
    public PageResponse<SystemConfigDTO.ConfigVO> list(Integer current, Integer size, String keyword, String group, String prefixes) {
        int page = current == null || current < 1 ? 1 : current;
        int pageSize = size == null || size < 1 ? 10 : size;
        List<String> prefixList = prefixes == null || prefixes.isBlank()
                ? List.of()
                : List.of(prefixes.split(",")).stream().map(String::trim).filter(item -> !item.isBlank()).toList();
        Specification<SystemConfig> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("configKey"), pattern),
                        cb.like(root.get("configGroup"), pattern),
                        cb.like(root.get("description"), pattern)
                ));
            }
            if (group != null && !group.isBlank()) {
                predicates.add(cb.equal(root.get("configGroup"), group.trim()));
            }
            if (!prefixList.isEmpty()) {
                List<Predicate> prefixPredicates = prefixList.stream()
                        .map(prefix -> cb.like(root.get("configKey"), prefix + "%"))
                        .toList();
                predicates.add(cb.or(prefixPredicates.toArray(new Predicate[0])));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<SystemConfig> result = systemConfigRepository.findAll(spec, PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "id")));
        List<SystemConfigDTO.ConfigVO> records = result.getContent().stream().map(this::toVO).toList();
        return new PageResponse<>(records, result.getTotalElements(), page, pageSize);
    }

    @Override
    public void update(Long id, SystemConfigDTO.UpdateRequest req) {
        SystemConfig config = systemConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "配置不存在"));
        config.setConfigValue(req.getConfigValue());
        config.setDescription(req.getDescription());
        config.setStatus(req.getStatus());
        systemConfigRepository.save(config);
    }

    @Override
    public void delete(Long id) {
        if (!systemConfigRepository.existsById(id)) {
            throw new BusinessException(404, "配置不存在");
        }
        systemConfigRepository.deleteById(id);
    }

    @Override
    public SystemConfigDTO.ConfigVO upsert(SystemConfigDTO.UpsertRequest req) {
        SystemConfig config = systemConfigRepository.findByConfigKey(req.getConfigKey().trim())
                .orElseGet(SystemConfig::new);
        config.setConfigKey(req.getConfigKey().trim());
        config.setConfigValue(req.getConfigValue());
        config.setConfigGroup(req.getConfigGroup() == null || req.getConfigGroup().isBlank() ? "ai" : req.getConfigGroup().trim());
        config.setDescription(req.getDescription());
        config.setStatus(req.getStatus());
        return toVO(systemConfigRepository.save(config));
    }

    @Override
    public SystemConfigDTO.TestResultVO test(Long id) {
        SystemConfig config = systemConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "配置不存在"));
        if (config.getConfigKey().startsWith("tencent.map.")) {
            return testTencentMap(config);
        }
        if (config.getConfigKey().startsWith("aliyun.oss.")) {
            return testAliyunOss(config);
        }
        if (config.getConfigKey().startsWith("ai.")) {
            return testAiConfig(config);
        }
        throw new BusinessException(400, "该配置项不支持连通测试");
    }

    private SystemConfigDTO.ConfigVO toVO(SystemConfig item) {
        SystemConfigDTO.ConfigVO vo = new SystemConfigDTO.ConfigVO();
        vo.setId(item.getId());
        vo.setConfigKey(item.getConfigKey());
        vo.setConfigValue(item.getConfigValue());
        vo.setConfigGroup(item.getConfigGroup());
        vo.setDescription(item.getDescription());
        vo.setStatus(item.getStatus());
        vo.setStatusText(Integer.valueOf(1).equals(item.getStatus()) ? "启用" : "禁用");
        vo.setTestable(item.getConfigKey().startsWith("tencent.map.") || item.getConfigKey().startsWith("aliyun.oss.") || item.getConfigKey().startsWith("ai."));
        vo.setUpdateTime(item.getUpdateTime() != null ? item.getUpdateTime().format(FMT) : null);
        return vo;
    }

    private SystemConfigDTO.TestResultVO testTencentMap(SystemConfig currentConfig) {
        String key = getConfigValue("tencent.map.key");
        String baseUrl = getConfigValue("tencent.map.base-url");
        String json = tencentMapWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(baseUrl != null && baseUrl.startsWith("http://") ? "http" : "https")
                        .host(extractHost(baseUrl))
                        .path("/ws/geocoder/v1/")
                        .queryParam("address", "北京天安门")
                        .queryParam("key", key)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        SystemConfigDTO.TestResultVO vo = new SystemConfigDTO.TestResultVO();
        vo.setId(currentConfig.getId());
        vo.setConfigKey(currentConfig.getConfigKey());
        vo.setTarget(baseUrl);
        vo.setSuccess(json != null && json.contains("\"status\":0"));
        vo.setDetail(vo.getSuccess() ? "腾讯地图 Key 调用成功" : "腾讯地图 Key 调用失败，请检查 key 和 base-url");
        return vo;
    }

    private SystemConfigDTO.TestResultVO testAliyunOss(SystemConfig currentConfig) {
        String endpoint = getConfigValue("aliyun.oss.endpoint");
        String bucketName = getConfigValue("aliyun.oss.bucket-name");
        String accessKeyId = getConfigValue("aliyun.oss.access-key-id");
        String accessKeySecret = getConfigValue("aliyun.oss.access-key-secret");

        boolean success;
        String detail;
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            success = ossClient.doesBucketExist(bucketName);
            detail = success ? "OSS 连接成功，Bucket 可访问" : "OSS 连接成功，但 Bucket 不可访问";
        } catch (Exception error) {
            success = false;
            detail = "OSS 连通失败：" + error.getMessage();
        } finally {
            ossClient.shutdown();
        }

        SystemConfigDTO.TestResultVO vo = new SystemConfigDTO.TestResultVO();
        vo.setId(currentConfig.getId());
        vo.setConfigKey(currentConfig.getConfigKey());
        vo.setTarget(endpoint + "/" + bucketName);
        vo.setSuccess(success);
        vo.setDetail(detail);
        return vo;
    }

    private SystemConfigDTO.TestResultVO testAiConfig(SystemConfig currentConfig) {
        if (currentConfig.getConfigKey().startsWith("ai.asr.xfyun.")) {
            return testXfyunAsrConfig(currentConfig);
        }
        String configPrefix = extractAiConfigPrefix(currentConfig.getConfigKey());
        String baseUrl = getAiConfigValue(configPrefix, "base-url");
        String apiKey = getAiConfigValue(configPrefix, "api-key");

        boolean success;
        String detail;
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            success = false;
            detail = "AI 接口配置不完整，请维护 " + configPrefix + ".base-url 和 " + configPrefix + ".api-key";
        } else try {
            String body = WebClient.builder()
                    .baseUrl(trimTrailingSlash(baseUrl))
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build()
                    .get()
                    .uri("/models")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            success = body != null && !body.isBlank();
            detail = success ? "AI 接口连通成功，/models 已返回响应" : "AI 接口无返回内容";
        } catch (WebClientResponseException error) {
            success = false;
            detail = "AI 接口返回异常：" + error.getStatusCode().value() + " " + error.getResponseBodyAsString();
        } catch (Exception error) {
            success = false;
            detail = "AI 接口连通失败：" + error.getMessage();
        }

        SystemConfigDTO.TestResultVO vo = new SystemConfigDTO.TestResultVO();
        vo.setId(currentConfig.getId());
        vo.setConfigKey(currentConfig.getConfigKey());
        vo.setTarget(trimTrailingSlash(baseUrl) + "/models");
        vo.setSuccess(success);
        vo.setDetail(detail);
        return vo;
    }

    private SystemConfigDTO.TestResultVO testXfyunAsrConfig(SystemConfig currentConfig) {
        String websocketUrl = getConfigValue("ai.asr.xfyun.websocket-url");
        String appId = getConfigValue("ai.asr.xfyun.app-id");
        String accessKeyId = getConfigValue("ai.asr.xfyun.access-key-id");
        String accessKeySecret = getConfigValue("ai.asr.xfyun.access-key-secret");
        String lang = getConfigValue("ai.asr.xfyun.lang");
        String audioEncode = getConfigValue("ai.asr.xfyun.audio-encode");
        String sampleRate = getConfigValue("ai.asr.xfyun.samplerate");

        boolean success = !websocketUrl.isBlank()
                && !appId.isBlank()
                && !accessKeyId.isBlank()
                && !accessKeySecret.isBlank()
                && !lang.isBlank()
                && !audioEncode.isBlank()
                && !sampleRate.isBlank();
        String detail = success
                ? testXfyunAsrHandshake(websocketUrl, appId, accessKeyId, accessKeySecret, lang, audioEncode, sampleRate)
                : "讯飞实时转写大模型配置不完整，请维护 ai.asr.xfyun.websocket-url、app-id、access-key-id、access-key-secret、lang、audio-encode 和 samplerate";
        success = success && detail.startsWith("讯飞实时转写大模型握手成功");

        SystemConfigDTO.TestResultVO vo = new SystemConfigDTO.TestResultVO();
        vo.setId(currentConfig.getId());
        vo.setConfigKey(currentConfig.getConfigKey());
        vo.setTarget(websocketUrl);
        vo.setSuccess(success);
        vo.setDetail(detail);
        return vo;
    }

    private String testXfyunAsrHandshake(String websocketUrl,
                                         String appId,
                                         String accessKeyId,
                                         String accessKeySecret,
                                         String lang,
                                         String audioEncode,
                                         String sampleRate) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        try {
            String uri = buildXfyunAsrUri(websocketUrl, appId, accessKeyId, accessKeySecret, lang, audioEncode, sampleRate, sessionId);
            CompletableFuture<String> firstMessage = new CompletableFuture<>();
            WebSocket socket = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
                    .newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(uri), new WebSocket.Listener() {
                        private final StringBuilder textBuffer = new StringBuilder();

                        @Override
                        public void onOpen(WebSocket webSocket) {
                            webSocket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            textBuffer.append(data);
                            if (last && !firstMessage.isDone()) {
                                firstMessage.complete(textBuffer.toString());
                                textBuffer.setLength(0);
                            }
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            firstMessage.completeExceptionally(error);
                        }
                    })
                    .orTimeout(8, TimeUnit.SECONDS)
                    .join();
            socket.sendText("{\"end\":true,\"sessionId\":\"" + sessionId + "\"}", true);
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "config_test");

            String message;
            try {
                message = firstMessage.get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                message = "";
            }
            return message.isBlank()
                    ? "讯飞实时转写大模型握手成功"
                    : "讯飞实时转写大模型握手成功，首条响应：" + abbreviate(message, 240);
        } catch (Exception error) {
            Throwable root = unwrap(error);
            return "讯飞实时转写大模型握手失败：" + root.getClass().getSimpleName() + ": " + root.getMessage();
        }
    }

    private String buildXfyunAsrUri(String websocketUrl,
                                    String appId,
                                    String accessKeyId,
                                    String accessKeySecret,
                                    String lang,
                                    String audioEncode,
                                    String sampleRate,
                                    String sessionId) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("accessKeyId", accessKeyId);
        params.put("appId", appId);
        params.put("audio_encode", audioEncode);
        params.put("lang", lang);
        params.put("samplerate", sampleRate);
        params.put("utc", OffsetDateTime.now(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")));
        params.put("uuid", sessionId);
        String baseString = buildQuery(params);
        return trimTrailingSlash(websocketUrl) + "?" + baseString + "&signature=" + encode(buildSignature(baseString, accessKeySecret));
    }

    private String buildQuery(TreeMap<String, String> params) {
        List<String> parts = new ArrayList<>();
        params.forEach((key, value) -> parts.add(encode(key) + "=" + encode(value)));
        return String.join("&", parts);
    }

    private String buildSignature(String baseString, String accessKeySecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("签名生成失败: " + e.getMessage(), e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String extractAiConfigPrefix(String configKey) {
        if (configKey == null || !configKey.startsWith("ai.service.")) {
            return "ai.service.text";
        }
        for (String field : List.of(".provider", ".base-url", ".api-key", ".model")) {
            if (configKey.endsWith(field)) {
                return configKey.substring(0, configKey.length() - field.length());
            }
        }
        return "ai.service.text";
    }

    private String getAiConfigValue(String configPrefix, String field) {
        return getConfigValue(configPrefix + "." + field);
    }

    private String getConfigValue(String key) {
        return systemConfigRepository.findByConfigKeyAndStatus(key, 1)
                .map(SystemConfig::getConfigValue)
                .orElse("");
    }

    private String extractHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return "apis.map.qq.com";
        return baseUrl.replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "";
        return value.replaceAll("/+$", "");
    }
}
