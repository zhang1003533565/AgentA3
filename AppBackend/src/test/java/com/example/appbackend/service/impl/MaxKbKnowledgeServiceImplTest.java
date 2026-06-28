package com.example.appbackend.service.impl;

import com.example.appbackend.dto.MaxKbKnowledgeDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.MaxKbAccount;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.MaxKbAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class MaxKbKnowledgeServiceImplTest {

    @Test
    void createAccount_shouldStoreMultipleAccountConnectionFieldsAndMaskApiKey() {
        List<MaxKbAccount> store = new ArrayList<>();
        MaxKbKnowledgeServiceImpl service = newService(store);

        MaxKbKnowledgeDTO.AccountCreateRequest request = new MaxKbKnowledgeDTO.AccountCreateRequest();
        request.setAccountName("教务知识库账号");
        request.setBaseUrl("http://maxkb.local/");
        request.setEnvironment("test");
        request.setApiKey("mkb_1234567890abcdef");
        request.setWorkspaceId("workspace-1");
        request.setRemark("只读账号");
        request.setStatus(1);

        MaxKbKnowledgeDTO.AccountVO response = service.createAccount(request);

        Assertions.assertEquals(1L, response.getId());
        Assertions.assertEquals("教务知识库账号", response.getAccountName());
        Assertions.assertEquals("http://maxkb.local", response.getBaseUrl());
        Assertions.assertEquals("test", response.getEnvironment());
        Assertions.assertEquals("测试", response.getEnvironmentText());
        Assertions.assertEquals("workspace-1", response.getWorkspaceId());
        Assertions.assertTrue(response.getApiKeyConfigured());
        Assertions.assertEquals("mkb_****cdef", response.getApiKeyMasked());
        Assertions.assertEquals("mkb_1234567890abcdef", store.get(0).getApiKey());
    }

    @Test
    void updateAccount_shouldKeepOldApiKeyWhenRequestApiKeyBlank() {
        MaxKbAccount account = account(3L, "http://maxkb.old", "mkb_old_key", "ws-old", 1);
        List<MaxKbAccount> store = new ArrayList<>(List.of(account));
        MaxKbKnowledgeServiceImpl service = newService(store);

        MaxKbKnowledgeDTO.AccountUpdateRequest request = new MaxKbKnowledgeDTO.AccountUpdateRequest();
        request.setAccountName("新账号名");
        request.setBaseUrl("http://maxkb.new/");
        request.setEnvironment("prod");
        request.setApiKey("");
        request.setWorkspaceId("ws-new");
        request.setStatus(1);

        MaxKbKnowledgeDTO.AccountVO response = service.updateAccount(3L, request);

        Assertions.assertEquals("新账号名", response.getAccountName());
        Assertions.assertEquals("http://maxkb.new", response.getBaseUrl());
        Assertions.assertEquals("prod", response.getEnvironment());
        Assertions.assertEquals("线上", response.getEnvironmentText());
        Assertions.assertEquals("ws-new", response.getWorkspaceId());
        Assertions.assertEquals("mkb_old_key", store.get(0).getApiKey());
    }

    @Test
    void listAccounts_shouldReturnStoredAccountsWithoutRawApiKey() {
        List<MaxKbAccount> store = new ArrayList<>(List.of(
                account(1L, "http://maxkb-a", "mkb_account_a", "ws-a", 1),
                account(2L, "http://maxkb-b", "mkb_account_b", "ws-b", 0)
        ));
        MaxKbKnowledgeServiceImpl service = newService(store);

        PageResponse<MaxKbKnowledgeDTO.AccountVO> page = service.listAccounts(1, 10, null, null, null);

        Assertions.assertEquals(2, page.getTotal());
        Assertions.assertEquals("测试账号1", page.getRecords().get(0).getAccountName());
        Assertions.assertEquals("local", page.getRecords().get(0).getEnvironment());
        Assertions.assertEquals("mkb_****nt_a", page.getRecords().get(0).getApiKeyMasked());
        Assertions.assertEquals("禁用", page.getRecords().get(1).getStatusText());
    }

    @Test
    void listEnvironmentOptions_shouldExposeAdminEnvironmentChoices() {
        MaxKbKnowledgeServiceImpl service = newService(new ArrayList<>());

        List<MaxKbKnowledgeDTO.EnvironmentOption> options = service.listEnvironmentOptions();

        Assertions.assertEquals(List.of("local", "test", "prod", "custom"),
                options.stream().map(MaxKbKnowledgeDTO.EnvironmentOption::getValue).toList());
    }

    @Test
    void listKnowledges_shouldFailWhenAccountDisabled() {
        MaxKbKnowledgeServiceImpl service = newService(new ArrayList<>(List.of(
                account(9L, "http://localhost:65535", "mkb_disabled", "ws-3", 0)
        )));

        BusinessException error = Assertions.assertThrows(
                BusinessException.class,
                () -> service.listKnowledges(9L, Map.of())
        );

        Assertions.assertTrue(error.getMessage().contains("已禁用"));
    }

    @Test
    void internalRequestHelpers_shouldUseSelectedAccountWorkspaceAndBearerKey() throws Exception {
        MaxKbAccount account = account(7L, "http://maxkb.local/", "mkb_account_key", "ws-1", 1);
        MaxKbKnowledgeServiceImpl service = newService(new ArrayList<>(List.of(account)));
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page", "1");
        query.put("page_size", "2");

        Method buildUri = MaxKbKnowledgeServiceImpl.class.getDeclaredMethod(
                "buildUri",
                MaxKbAccount.class,
                String.class,
                Map.class
        );
        buildUri.setAccessible(true);
        String uri = (String) buildUri.invoke(service, account, "/workspaces/ws-1/knowledges", query);

        Method applyAuth = MaxKbKnowledgeServiceImpl.class.getDeclaredMethod(
                "applyMaxKbAuth",
                HttpHeaders.class,
                MaxKbAccount.class
        );
        applyAuth.setAccessible(true);
        HttpHeaders headers = new HttpHeaders();
        applyAuth.invoke(service, headers, account);

        Assertions.assertEquals(
                "http://maxkb.local/openapi/knowledge/v1/workspaces/ws-1/knowledges?page=1&page_size=2",
                uri
        );
        Assertions.assertEquals("Bearer mkb_account_key", headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    private MaxKbKnowledgeServiceImpl newService(List<MaxKbAccount> store) {
        return new MaxKbKnowledgeServiceImpl(
                WebClient.builder(),
                newMaxKbAccountRepository(store),
                new ObjectMapper(),
                5,
                1024 * 1024
        );
    }

    private MaxKbAccountRepository newMaxKbAccountRepository(List<MaxKbAccount> store) {
        return (MaxKbAccountRepository) Proxy.newProxyInstance(
                MaxKbAccountRepository.class.getClassLoader(),
                new Class<?>[]{MaxKbAccountRepository.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("toString".equals(methodName)) {
                        return "TestMaxKbAccountRepository";
                    }
                    if ("hashCode".equals(methodName)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(methodName)) {
                        return proxy == args[0];
                    }
                    if ("findById".equals(methodName)) {
                        Long id = (Long) args[0];
                        return store.stream().filter(item -> item.getId().equals(id)).findFirst();
                    }
                    if ("save".equals(methodName)) {
                        MaxKbAccount account = (MaxKbAccount) args[0];
                        if (account.getId() == null) {
                            account.setId((long) store.size() + 1);
                        }
                        account.setUpdateTime(LocalDateTime.now());
                        if (account.getCreateTime() == null) {
                            account.setCreateTime(account.getUpdateTime());
                        }
                        store.removeIf(item -> item.getId().equals(account.getId()));
                        store.add(account);
                        return account;
                    }
                    if ("delete".equals(methodName)) {
                        MaxKbAccount account = (MaxKbAccount) args[0];
                        store.removeIf(item -> item.getId().equals(account.getId()));
                        return null;
                    }
                    if ("findAll".equals(methodName)) {
                        return new org.springframework.data.domain.PageImpl<>(store);
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private MaxKbAccount account(Long id, String baseUrl, String apiKey, String workspaceId, Integer status) {
        MaxKbAccount account = new MaxKbAccount();
        account.setId(id);
        account.setAccountName("测试账号" + id);
        account.setBaseUrl(baseUrl);
        account.setEnvironment("local");
        account.setApiKey(apiKey);
        account.setWorkspaceId(workspaceId);
        account.setStatus(status);
        account.setCreateTime(LocalDateTime.now());
        account.setUpdateTime(LocalDateTime.now());
        return account;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (Optional.class.equals(returnType)) {
            return Optional.empty();
        }
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0F;
        }
        if (double.class.equals(returnType)) {
            return 0D;
        }
        return null;
    }
}
