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

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
