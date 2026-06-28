package com.example.appbackend.service.impl;

import com.example.appbackend.dto.MaxKbKnowledgeDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.MaxKbAccount;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.MaxKbAccountRepository;
import com.example.appbackend.service.MaxKbKnowledgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MaxKbKnowledgeServiceImpl implements MaxKbKnowledgeService {
    private static final String OPEN_API_PREFIX = "/openapi/knowledge/v1";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WebClient.Builder webClientBuilder;
    private final MaxKbAccountRepository maxKbAccountRepository;
    private final ObjectMapper objectMapper;
    private final long timeoutSeconds;
    private final int fileResponseMaxInMemoryBytes;

    public MaxKbKnowledgeServiceImpl(WebClient.Builder webClientBuilder,
                                     MaxKbAccountRepository maxKbAccountRepository,
                                     ObjectMapper objectMapper,
                                     @Value("${knowledge.maxkb.timeout-seconds:30}") long timeoutSeconds,
                                     @Value("${knowledge.maxkb.file-response-max-in-memory-bytes:52428800}") int fileResponseMaxInMemoryBytes) {
        this.webClientBuilder = webClientBuilder;
        this.maxKbAccountRepository = maxKbAccountRepository;
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;
        this.fileResponseMaxInMemoryBytes = fileResponseMaxInMemoryBytes;
    }

    @Override
    public List<MaxKbKnowledgeDTO.EnvironmentOption> listEnvironmentOptions() {
        return List.of(
                new MaxKbKnowledgeDTO.EnvironmentOption("local", "本地", "本机或局域网 MaxKB 服务地址"),
                new MaxKbKnowledgeDTO.EnvironmentOption("test", "测试", "测试环境 MaxKB 服务地址"),
                new MaxKbKnowledgeDTO.EnvironmentOption("prod", "线上", "生产环境 MaxKB 服务地址"),
                new MaxKbKnowledgeDTO.EnvironmentOption("custom", "自定义", "其他临时或专用 MaxKB 服务地址")
        );
    }

    @Override
    public PageResponse<MaxKbKnowledgeDTO.AccountVO> listAccounts(
            Integer current,
            Integer size,
            String keyword,
            String environment,
            Integer status
    ) {
        int page = current == null || current < 1 ? 1 : current;
        int pageSize = size == null || size < 1 ? 10 : size;
        Specification<MaxKbAccount> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("accountName"), pattern),
                        cb.like(root.get("baseUrl"), pattern),
                        cb.like(root.get("workspaceId"), pattern),
                        cb.like(root.get("remark"), pattern)
                ));
            }
            if (StringUtils.hasText(environment)) {
                predicates.add(cb.equal(root.get("environment"), normalizeEnvironment(environment)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<MaxKbAccount> result = maxKbAccountRepository.findAll(
                spec,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "updateTime", "id"))
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(),
                page,
                pageSize
        );
    }

    @Override
    public MaxKbKnowledgeDTO.AccountVO createAccount(MaxKbKnowledgeDTO.AccountCreateRequest request) {
        MaxKbAccount account = new MaxKbAccount();
        account.setAccountName(trim(request.getAccountName()));
        account.setBaseUrl(trimTrailingSlash(request.getBaseUrl()));
        account.setEnvironment(normalizeEnvironment(request.getEnvironment()));
        account.setApiKey(trim(request.getApiKey()));
        account.setWorkspaceId(trim(request.getWorkspaceId()));
        account.setRemark(trimToNull(request.getRemark()));
        account.setStatus(normalizeStatus(request.getStatus()));
        return toVO(maxKbAccountRepository.save(account));
    }

    @Override
    public MaxKbKnowledgeDTO.AccountVO updateAccount(Long accountId, MaxKbKnowledgeDTO.AccountUpdateRequest request) {
        MaxKbAccount account = getAccount(accountId, false);
        account.setAccountName(trim(request.getAccountName()));
        account.setBaseUrl(trimTrailingSlash(request.getBaseUrl()));
        account.setEnvironment(normalizeEnvironment(request.getEnvironment()));
        if (StringUtils.hasText(request.getApiKey())) {
            account.setApiKey(trim(request.getApiKey()));
        }
        account.setWorkspaceId(trim(request.getWorkspaceId()));
        account.setRemark(trimToNull(request.getRemark()));
        account.setStatus(normalizeStatus(request.getStatus()));
        return toVO(maxKbAccountRepository.save(account));
    }

    @Override
    public void deleteAccount(Long accountId) {
        MaxKbAccount account = getAccount(accountId, false);
        maxKbAccountRepository.delete(account);
    }

    @Override
    public MaxKbKnowledgeDTO.AccountVO updateAccountStatus(Long accountId, Integer status) {
        MaxKbAccount account = getAccount(accountId, false);
        account.setStatus(normalizeStatus(status));
        return toVO(maxKbAccountRepository.save(account));
    }

    @Override
    public Object testConnection(Long accountId) {
        MaxKbAccount account = getAccount(accountId, true);
        return getObject(account, "/workspaces/" + account.getWorkspaceId() + "/knowledges", Map.of("page", "1", "page_size", "1"));
    }

    @Override
    public Object docs(Long accountId) {
        return getObject(getAccount(accountId, true), "/docs", null);
    }

    @Override
    public Object listKnowledges(Long accountId, Map<String, String> queryParams) {
        MaxKbAccount account = getAccount(accountId, true);
        return getObject(account, "/workspaces/" + account.getWorkspaceId() + "/knowledges", queryParams);
    }

    @Override
    public Object getKnowledge(Long accountId, String knowledgeId) {
        MaxKbAccount account = getAccount(accountId, true);
        return getObject(account, "/workspaces/" + account.getWorkspaceId() + "/knowledges/" + requireId(knowledgeId, "知识库 ID"), null);
    }

    @Override
    public Object listDocuments(Long accountId, String knowledgeId, Map<String, String> queryParams) {
        MaxKbAccount account = getAccount(accountId, true);
        return getObject(
                account,
                "/workspaces/" + account.getWorkspaceId() + "/knowledges/" + requireId(knowledgeId, "知识库 ID") + "/documents",
                queryParams
        );
    }

    @Override
    public Object uploadDocuments(Long accountId,
                                  String knowledgeId,
                                  List<MultipartFile> files,
                                  Integer limit,
                                  List<String> patterns,
                                  Boolean withFilter,
                                  String splitStrategy,
                                  String modelId) {
        MaxKbAccount account = getAccount(accountId, true);
        if (files == null || files.stream().allMatch(file -> file == null || file.isEmpty())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "上传文件不能为空");
        }

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .forEach(file -> addFilePart(bodyBuilder, file));
        bodyBuilder.part("limit", String.valueOf(limit == null ? 4096 : limit));
        if (patterns != null) {
            patterns.stream()
                    .filter(StringUtils::hasText)
                    .forEach(pattern -> bodyBuilder.part("patterns", pattern.trim()));
        }
        if (withFilter != null) {
            bodyBuilder.part("with_filter", String.valueOf(withFilter));
        }
        if (StringUtils.hasText(splitStrategy)) {
            bodyBuilder.part("split_strategy", splitStrategy.trim());
        }
        if (StringUtils.hasText(modelId)) {
            bodyBuilder.part("model_id", modelId.trim());
        }

        String path = "/workspaces/" + account.getWorkspaceId()
                + "/knowledges/" + requireId(knowledgeId, "知识库 ID")
                + "/documents/upload";
        try {
            return buildMaxKbWebClient()
                    .post()
                    .uri(buildUri(account, path, null))
                    .headers(headers -> applyMaxKbAuth(headers, account))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException error) {
            throw new BusinessException(Result.ERROR_CODE, "MaxKB 文件上传失败: " + extractRemoteMessage(error));
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(Result.ERROR_CODE, "MaxKB 文件上传失败: " + error.getMessage());
        }
    }

    @Override
    public Object listParagraphs(Long accountId, String knowledgeId, String documentId, Map<String, String> queryParams) {
        MaxKbAccount account = getAccount(accountId, true);
        return getObject(
                account,
                "/workspaces/" + account.getWorkspaceId()
                        + "/knowledges/" + requireId(knowledgeId, "知识库 ID")
                        + "/documents/" + requireId(documentId, "文档 ID")
                        + "/paragraphs",
                queryParams
        );
    }

    @Override
    public Object hitTest(Long accountId, Map<String, Object> request) {
        MaxKbAccount account = getAccount(accountId, true);
        return postObject(account, "/workspaces/" + account.getWorkspaceId() + "/hit-test", request);
    }

    private Object getObject(MaxKbAccount account, String path, Map<String, String> queryParams) {
        try {
            return buildMaxKbWebClient()
                    .get()
                    .uri(buildUri(account, path, queryParams))
                    .headers(headers -> applyMaxKbAuth(headers, account))
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException error) {
            throw new BusinessException(Result.ERROR_CODE, "MaxKB 服务调用失败: " + extractRemoteMessage(error));
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(Result.ERROR_CODE, "MaxKB 服务调用失败: " + error.getMessage());
        }
    }

    private Object postObject(MaxKbAccount account, String path, Map<String, Object> request) {
        try {
            return buildMaxKbWebClient()
                    .post()
                    .uri(buildUri(account, path, null))
                    .headers(headers -> applyMaxKbAuth(headers, account))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request == null ? Map.of() : request)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException error) {
            throw new BusinessException(Result.ERROR_CODE, "MaxKB 服务调用失败: " + extractRemoteMessage(error));
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(Result.ERROR_CODE, "MaxKB 服务调用失败: " + error.getMessage());
        }
    }

    private MaxKbAccount getAccount(Long accountId, boolean requireEnabled) {
        if (accountId == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "MaxKB 账号 ID 不能为空");
        }
        MaxKbAccount account = maxKbAccountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "MaxKB 账号不存在"));
        if (requireEnabled && !Integer.valueOf(1).equals(account.getStatus())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "MaxKB 账号已禁用");
        }
        if (requireEnabled) {
            validateAccountConfig(account);
        }
        return account;
    }

    private void validateAccountConfig(MaxKbAccount account) {
        if (!StringUtils.hasText(account.getBaseUrl())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "MaxKB 服务地址未配置");
        }
        if (!StringUtils.hasText(account.getApiKey())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "MaxKB OpenAPI Key 未配置");
        }
        if (!StringUtils.hasText(account.getWorkspaceId())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "MaxKB 工作空间 ID 未配置");
        }
    }

    private void addFilePart(MultipartBodyBuilder bodyBuilder, MultipartFile file) {
        try {
            String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "document";
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            var partBuilder = bodyBuilder.part("file", resource).filename(filename);
            if (StringUtils.hasText(file.getContentType())) {
                partBuilder.contentType(MediaType.parseMediaType(file.getContentType()));
            }
        } catch (Exception error) {
            throw new BusinessException(Result.ERROR_CODE, "读取上传文件失败: " + error.getMessage());
        }
    }

    private WebClient buildMaxKbWebClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(fileResponseMaxInMemoryBytes))
                .build();
        return webClientBuilder.clone()
                .exchangeStrategies(strategies)
                .build();
    }

    private void applyMaxKbAuth(HttpHeaders headers, MaxKbAccount account) {
        headers.setBearerAuth(account.getApiKey().trim());
    }

    private String buildUri(MaxKbAccount account, String path, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(trimTrailingSlash(account.getBaseUrl()) + OPEN_API_PREFIX + path);
        if (queryParams != null) {
            queryParams.forEach((key, value) -> {
                if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                    builder.queryParam(key, value);
                }
            });
        }
        return builder.build().toUriString();
    }

    private MaxKbKnowledgeDTO.AccountVO toVO(MaxKbAccount account) {
        MaxKbKnowledgeDTO.AccountVO vo = new MaxKbKnowledgeDTO.AccountVO();
        vo.setId(account.getId());
        vo.setAccountName(account.getAccountName());
        vo.setBaseUrl(account.getBaseUrl());
        vo.setEnvironment(account.getEnvironment());
        vo.setEnvironmentText(environmentText(account.getEnvironment()));
        vo.setWorkspaceId(account.getWorkspaceId());
        vo.setRemark(account.getRemark());
        vo.setStatus(account.getStatus());
        vo.setStatusText(Integer.valueOf(1).equals(account.getStatus()) ? "启用" : "禁用");
        vo.setApiKeyConfigured(StringUtils.hasText(account.getApiKey()));
        vo.setApiKeyMasked(maskSecret(account.getApiKey()));
        vo.setCreateTime(account.getCreateTime() == null ? null : account.getCreateTime().format(FMT));
        vo.setUpdateTime(account.getUpdateTime() == null ? null : account.getUpdateTime().format(FMT));
        return vo;
    }

    private String normalizeEnvironment(String environment) {
        String value = trim(environment).toLowerCase();
        if (List.of("local", "test", "prod", "custom").contains(value)) {
            return value;
        }
        return "custom";
    }

    private String environmentText(String environment) {
        return switch (normalizeEnvironment(environment)) {
            case "local" -> "本地";
            case "test" -> "测试";
            case "prod" -> "线上";
            default -> "自定义";
        };
    }

    private Integer normalizeStatus(Integer status) {
        return Integer.valueOf(0).equals(status) ? 0 : 1;
    }

    private String requireId(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, label + "不能为空");
        }
        return value.trim();
    }

    private String extractRemoteMessage(WebClientResponseException error) {
        String body = error.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return error.getMessage();
        }
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                for (String key : List.of("detail", "message", "msg", "error")) {
                    Object value = map.get(key);
                    if (value != null) {
                        return value.toString();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return body;
    }

    private String maskSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed.isBlank() ? null : trimmed;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimTrailingSlash(String value) {
        String trimmed = trim(value);
        return trimmed.replaceAll("/+$", "");
    }
}
