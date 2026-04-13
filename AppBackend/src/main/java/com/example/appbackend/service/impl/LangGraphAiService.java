package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.CanteenStall;
import com.example.appbackend.entity.CourseSchedule;
import com.example.appbackend.entity.Dish;
import com.example.appbackend.entity.Merchant;
import com.example.appbackend.entity.PromotionCoupon;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.graph.AiConversationState;
import com.example.appbackend.repository.CanteenStallRepository;
import com.example.appbackend.repository.DishRepository;
import com.example.appbackend.repository.FacilityRepository;
import com.example.appbackend.repository.MerchantRepository;
import com.example.appbackend.repository.PromotionCouponRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.CourseScheduleService;
import com.example.appbackend.service.LlmMemoryService;
import com.example.appbackend.util.JwtUtil;
import com.example.appbackend.util.LlmConfigUtil;
import com.example.appbackend.util.WeekCalculator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Service
public class LangGraphAiService {

    private static final String DEFAULT_PROMPT = "你是智慧校园助手，请基于已有上下文和用户输入进行清晰、简洁的回答。";

    private final LlmMemoryService llmMemoryService;
    private final LlmConfigUtil llmConfigUtil;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final FacilityRepository facilityRepository;
    private final CanteenStallRepository canteenStallRepository;
    private final DishRepository dishRepository;
    private final PromotionCouponRepository promotionCouponRepository;
    private final MerchantRepository merchantRepository;
    private final CourseScheduleService courseScheduleService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final org.bsc.langgraph4j.CompiledGraph<AiConversationState> compiledGraph;

    public LangGraphAiService(LlmMemoryService llmMemoryService,
                              LlmConfigUtil llmConfigUtil,
                              WebClient.Builder webClientBuilder,
                              ObjectMapper objectMapper,
                              FacilityRepository facilityRepository,
                              CanteenStallRepository canteenStallRepository,
                              DishRepository dishRepository,
                              PromotionCouponRepository promotionCouponRepository,
                              MerchantRepository merchantRepository,
                              CourseScheduleService courseScheduleService,
                              UserRepository userRepository,
                              JwtUtil jwtUtil) {
        this.llmMemoryService = llmMemoryService;
        this.llmConfigUtil = llmConfigUtil;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.facilityRepository = facilityRepository;
        this.canteenStallRepository = canteenStallRepository;
        this.dishRepository = dishRepository;
        this.promotionCouponRepository = promotionCouponRepository;
        this.merchantRepository = merchantRepository;
        this.courseScheduleService = courseScheduleService;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.compiledGraph = buildGraph();
    }

    public LlmChatResponse chat(LlmChatRequest request, String token) {
        return executeChat(request, token);
    }

    public SseEmitter streamChat(LlmChatRequest request, String token) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                LlmChatResponse response = executeChat(request, token);
                sendEvent(emitter, "session", Map.of(
                        "sessionId", response.getSessionId(),
                        "sessionToken", response.getSessionToken(),
                        "model", response.getModel()
                ));
                sendEvent(emitter, "search", Map.of(
                        "searchKeyword", response.getSearchKeyword(),
                        "matchedResults", response.getMatchedResults()
                ));
                for (String chunk : chunkAnswer(response.getAnswer())) {
                    sendEvent(emitter, "delta", Map.of("content", chunk));
                    sleepQuietly(35);
                }
                sendEvent(emitter, "done", Map.of(
                        "answer", response.getAnswer(),
                        "searchKeyword", response.getSearchKeyword(),
                        "matchedResults", response.getMatchedResults()
                ));
                emitter.complete();
            } catch (Exception e) {
                try {
                    sendEvent(emitter, "error", Map.of(
                            "message", e.getMessage()
                    ));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private LlmChatResponse executeChat(LlmChatRequest request, String token) {
        if (!llmConfigUtil.hasApiKey()) {
            throw new BusinessException(Result.ERROR_CODE, "未配置 DEEPSEEK_API_KEY");
        }

        String sessionId = llmMemoryService.getOrCreateSessionId(token, request.getSessionId());
        String sessionToken = llmMemoryService.resolveSessionToken(token, sessionId);
        String prompt = StringUtils.hasText(request.getPrompt()) ? request.getPrompt() : DEFAULT_PROMPT;
        Long userId = extractUserId(token);

        Map<String, Object> initData = new LinkedHashMap<>();
        initData.put(AiConversationState.SESSION_ID, sessionId);
        initData.put(AiConversationState.SESSION_TOKEN, sessionToken);
        initData.put(AiConversationState.USER_ID, userId);
        initData.put(AiConversationState.MODEL, llmConfigUtil.getModel());
        initData.put(AiConversationState.PROMPT, prompt);
        initData.put(AiConversationState.INPUT, request.getInput());

        Optional<AiConversationState> finalState;
        try {
            finalState = compiledGraph.invoke(initData, RunnableConfig.builder().build());
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "AI graph 执行失败: " + e.getMessage());
        }

        if (finalState.isEmpty() || !StringUtils.hasText(finalState.get().answer())) {
            throw new BusinessException(Result.ERROR_CODE, "AI graph 未生成有效回答");
        }

        AiConversationState state = finalState.get();
        return new LlmChatResponse(
                sessionId,
                sessionToken,
                state.model(),
                state.searchKeyword(),
                state.searchResults(),
                state.answer()
        );
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws Exception {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(data, MediaType.APPLICATION_JSON));
    }

    private List<String> chunkAnswer(String answer) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(answer)) {
            return chunks;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < answer.length(); i++) {
            char ch = answer.charAt(i);
            current.append(ch);
            boolean shouldFlush = current.length() >= 12
                    || "，。！？；：,.!?;\n".indexOf(ch) >= 0;
            if (shouldFlush) {
                chunks.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private org.bsc.langgraph4j.CompiledGraph<AiConversationState> buildGraph() {
        try {
            return new StateGraph<AiConversationState>(Map.of(), AiConversationState::new)
                    .addNode("loadMemory", node_async(new LoadMemoryNode()))
                    .addNode("extractKeyword", node_async(new ExtractKeywordNode()))
                    .addNode("searchCanteen", node_async(new SearchCanteenNode()))
                    .addNode("buildPrompt", node_async(new BuildPromptNode()))
                    .addNode("callLlm", node_async(new CallLlmNode()))
                    .addNode("saveMemory", node_async(new SaveMemoryNode()))
                    .addEdge(START, "loadMemory")
                    .addEdge("loadMemory", "extractKeyword")
                    .addEdge("extractKeyword", "searchCanteen")
                    .addEdge("searchCanteen", "buildPrompt")
                    .addEdge("buildPrompt", "callLlm")
                    .addEdge("callLlm", "saveMemory")
                    .addEdge("saveMemory", END)
                    .compile();
        } catch (GraphStateException e) {
            throw new IllegalStateException("初始化 LangGraph AI workflow 失败", e);
        }
    }

    private class LoadMemoryNode implements NodeAction<AiConversationState> {
        @Override
        public Map<String, Object> apply(AiConversationState state) {
            List<Map<String, String>> history = llmMemoryService.getHistoryMessages(state.sessionToken());
            return Map.of(AiConversationState.HISTORY, history);
        }
    }

    private class ExtractKeywordNode implements NodeAction<AiConversationState> {
        @Override
        public Map<String, Object> apply(AiConversationState state) {
            if (isScheduleIntent(state.input())) {
                return Map.of(AiConversationState.SEARCH_KEYWORD, "课表查询");
            }
            String keyword = extractSearchKeyword(state.input());
            return Map.of(AiConversationState.SEARCH_KEYWORD, keyword);
        }
    }

    private class SearchCanteenNode implements NodeAction<AiConversationState> {
        @Override
        public Map<String, Object> apply(AiConversationState state) {
            List<Map<String, Object>> results = isScheduleIntent(state.input())
                    ? searchScheduleByInput(state.input(), state.userId())
                    : searchByKeyword(state.searchKeyword());
            return Map.of(AiConversationState.SEARCH_RESULTS, results);
        }
    }

    private class BuildPromptNode implements NodeAction<AiConversationState> {
        @Override
        public Map<String, Object> apply(AiConversationState state) {
            List<Map<String, String>> requestMessages = new ArrayList<>();
            requestMessages.add(message("system", state.prompt()));
            if (StringUtils.hasText(state.searchKeyword()) || (state.searchResults() != null && !state.searchResults().isEmpty())) {
                requestMessages.add(message("system", buildSearchFactsPrompt(state.searchKeyword(), state.searchResults())));
            }
            requestMessages.addAll(toLlmMessages(state.history()));
            requestMessages.add(message("user", state.input()));
            return Map.of(AiConversationState.REQUEST_MESSAGES, requestMessages);
        }
    }

    private class CallLlmNode implements NodeAction<AiConversationState> {
        @Override
        public Map<String, Object> apply(AiConversationState state) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", state.model());
            body.put("messages", state.requestMessages());
            body.put("stream", false);

            String responseText = webClientBuilder.build()
                    .post()
                    .uri(buildChatUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + llmConfigUtil.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            return Map.of(AiConversationState.ANSWER, extractAnswer(parseResponse(responseText)));
        }
    }

    private class SaveMemoryNode implements NodeAction<AiConversationState> {
        @Override
        public Map<String, Object> apply(AiConversationState state) {
            llmMemoryService.appendConversation(state.sessionToken(), state.input(), state.answer());
            return Map.of();
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    private List<Map<String, String>> toLlmMessages(List<Map<String, String>> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (Map<String, String> item : history) {
            String role = item.get("role");
            String content = item.get("content");
            if (!StringUtils.hasText(role) || !StringUtils.hasText(content)) {
                continue;
            }
            if ("ai".equalsIgnoreCase(role)) {
                role = "assistant";
            }
            messages.add(message(role, content));
        }
        return messages;
    }

    private String buildChatUri() {
        String baseUrl = llmConfigUtil.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(Result.ERROR_CODE, "未配置 DeepSeek base-url");
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!normalized.endsWith("/v1")) {
            normalized = normalized + "/v1";
        }
        return normalized + "/chat/completions";
    }

    private String extractSearchKeyword(String input) {
        if (!StringUtils.hasText(input)) {
            return "";
        }

        String extractionPrompt = "你是一个搜索词提取器。"
                + "请从用户的问题中提取最适合做校园食堂/档口/菜品搜索的核心关键词。"
                + "只返回关键词本身，不要解释，不要标点，不要句子。"
                + "如果问题中已经有明确菜名、品类或店铺特征，就优先返回那个词。"
                + "例如：'那家的麻辣烫好吃' 返回 '麻辣烫'；"
                + "'哪个食堂有黄焖鸡' 返回 '黄焖鸡'。";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", llmConfigUtil.getModel());
        body.put("messages", List.of(
                message("system", extractionPrompt),
                message("user", input)
        ));
        body.put("stream", false);

        String responseText = webClientBuilder.build()
                .post()
                .uri(buildChatUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + llmConfigUtil.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        String keyword = extractAnswer(parseResponse(responseText));
        keyword = sanitizeKeyword(keyword);
        if (!StringUtils.hasText(keyword)) {
            keyword = sanitizeKeyword(input);
        }
        return keyword;
    }

    private Long extractUserId(String authorization) {
        try {
            String token = normalizeBearerToken(authorization);
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "未登录或Token无效");
        }
    }

    private String normalizeBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "未登录或Token无效");
        }
        return authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
    }

    private boolean isScheduleIntent(String input) {
        if (!StringUtils.hasText(input)) {
            return false;
        }
        String text = normalizeText(input);
        return text.contains("课表")
                || text.contains("课程安排")
                || text.contains("下节课")
                || text.contains("下一节课")
                || text.contains("再下一节课")
                || text.contains("后一节课")
                || text.contains("下一门课")
                || text.contains("再下一门课")
                || text.contains("下门课")
                || text.contains("今天有什么课")
                || text.contains("明天有什么课")
                || text.contains("后天有什么课")
                || text.contains("今天有课吗")
                || text.contains("明天有课吗")
                || text.contains("本周有什么课")
                || text.contains("这周有什么课")
                || text.contains("接下来有什么课")
                || text.contains("接下来哪门课")
                || text.contains("第几节有课")
                || text.matches(".*第\\d+周.*")
                || text.matches(".*周[一二三四五六日天].*有.*课.*")
                || text.matches(".*星期[一二三四五六日天].*有.*课.*");
    }

    private List<Map<String, Object>> searchScheduleByInput(String input, Long userId) {
        if (userId == null) {
            return List.of();
        }

        Integer requestedWeek = resolveRequestedWeek(input, userId);
        Integer requestedWeekday = resolveRequestedWeekday(input);

        List<CourseSchedule> schedules = requestedWeek != null
                ? courseScheduleService.getWeekSchedule(userId, requestedWeek)
                : courseScheduleService.getCurrentWeekSchedule(userId);

        if (requestedWeekday != null) {
            schedules = schedules.stream()
                    .filter(item -> requestedWeekday.equals(item.getWeekday()))
                    .toList();
        }

        return schedules.stream()
                .sorted(Comparator
                        .comparing(CourseSchedule::getWeekday, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(item -> parseSessionStart(item.getClassSessions()), Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(12)
                .map(item -> courseScheduleResult(item, requestedWeek, requestedWeekday))
                .toList();
    }

    private List<Map<String, Object>> searchByKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String normalizedKeyword = normalizeText(keyword);

        List<Map<String, Object>> results = new ArrayList<>();
        Set<Long> matchedRestaurantIds = new HashSet<>();
        Set<Long> matchedStallIds = new HashSet<>();
        Set<Long> couponIds = new HashSet<>();

        facilityRepository.findByFacilityType(CampusFacility.FacilityType.RESTAURANT.getValue()).stream()
                .filter(item -> containsKeyword(normalizedKeyword, item.getFacilityName(), item.getDescription(), item.getLocation()))
                .limit(5)
                .forEach(item -> {
                    results.add(restaurantResult(item));
                    matchedRestaurantIds.add(item.getId());
                });

        canteenStallRepository.findAll().stream()
                .filter(item -> item.getStatus() != null && item.getStatus() == 1)
                .filter(item -> containsKeyword(normalizedKeyword, item.getStallName(), item.getCategory(), item.getDescription(), item.getLocation()))
                .sorted(Comparator.comparing(CanteenStall::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .forEach(item -> {
                    results.add(stallResult(item));
                    matchedStallIds.add(item.getId());
                    if (item.getRestaurantId() != null) {
                        matchedRestaurantIds.add(item.getRestaurantId());
                    }
                });

        dishRepository.findAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsAvailable()))
                .filter(item -> containsKeyword(normalizedKeyword, item.getName(), item.getCategory(), item.getTaste(), item.getDescription()))
                .sorted(Comparator.comparing(Dish::getRating, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .forEach(item -> {
                    results.add(dishResult(item));
                    if (item.getStallId() != null) {
                        matchedStallIds.add(item.getStallId());
                        CanteenStall stall = canteenStallRepository.findById(item.getStallId()).orElse(null);
                        if (stall != null && stall.getRestaurantId() != null) {
                            matchedRestaurantIds.add(stall.getRestaurantId());
                        }
                    }
                });

        promotionCouponRepository.findByStatusOrderBySortOrderAsc(1).stream()
                .filter(item -> containsKeyword(normalizedKeyword,
                        item.getCouponName(),
                        item.getDescription(),
                        item.getPickupLocation(),
                        item.getTagType(),
                        resolveCouponMerchantName(item),
                        resolveCouponStallName(item),
                        resolveCouponFacilityName(item)))
                .limit(8)
                .forEach(item -> {
                    results.add(couponResult(item));
                    couponIds.add(item.getId());
                });

        collectRelatedCouponsByStalls(matchedStallIds, results, couponIds);
        collectRelatedCouponsByFacilities(matchedRestaurantIds, results, couponIds);

        while (results.size() > 10) {
            results.remove(results.size() - 1);
        }
        return results;
    }

    private String buildSearchFactsPrompt(String keyword, List<Map<String, Object>> searchResults) {
        if ("课表查询".equals(keyword)) {
            if (searchResults == null || searchResults.isEmpty()) {
                return "系统已执行课表查询，但当前没有查到匹配课程。请明确告诉用户：可能本周无课、当天无课，或尚未导入课表。";
            }
            try {
                return "系统已经完成当前用户的课表查询。下面这些是受控查询返回的课程结果，请严格依据这些课程信息回答，不要编造不存在的课程安排："
                        + objectMapper.writeValueAsString(searchResults);
            } catch (Exception e) {
                return "系统已经完成当前用户的课表查询，并找到了 " + searchResults.size() + " 条课程结果。";
            }
        }
        if (searchResults == null || searchResults.isEmpty()) {
            return "系统搜索关键词为：" + keyword + "。当前没有找到完全匹配的食堂/档口/菜品，请你明确告诉用户未匹配到，并可以给出更适合搜索的关键词建议。";
        }
        try {
            return "系统已经根据关键词 `" + keyword + "` 做了本地词语匹配。"
                    + "下面这些是受控搜索函数返回的精确候选，请优先依据这些结果回答，不要编造不存在的数据："
                    + objectMapper.writeValueAsString(searchResults);
        } catch (Exception e) {
            return "系统已经根据关键词 `" + keyword + "` 做了本地词语匹配，并找到了 " + searchResults.size() + " 条候选数据。";
        }
    }

    private JsonNode parseResponse(String responseText) {
        if (!StringUtils.hasText(responseText)) {
            throw new BusinessException(Result.ERROR_CODE, "LLM 服务无响应");
        }
        try {
            return objectMapper.readTree(responseText);
        } catch (Exception e) {
            throw new BusinessException(Result.ERROR_CODE, "LLM 响应解析失败");
        }
    }

    private String extractAnswer(JsonNode response) {
        if (response == null) {
            throw new BusinessException(Result.ERROR_CODE, "LLM 服务无响应");
        }
        String answer = response.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException(Result.ERROR_CODE, "LLM 返回内容为空");
        }
        return answer;
    }

    private boolean containsKeyword(String keyword, String... fields) {
        return Stream.of(fields)
                .filter(StringUtils::hasText)
                .map(this::normalizeText)
                .anyMatch(field -> field.contains(keyword));
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[\\s，。！？,.!？、“”\"'：:（）()]", "");
    }

    private String sanitizeKeyword(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        normalized = normalized.replaceAll("关键词[:：]?", "");
        normalized = normalized.replaceAll("[\\n\\r]", "");
        normalized = normalized.replaceAll("[。！!？，,；;]", "");
        normalized = normalized.trim();
        return normalized.length() > 24 ? normalized.substring(0, 24) : normalized;
    }

    private Integer resolveRequestedWeek(String input, Long userId) {
        if (!StringUtils.hasText(input) || userId == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("第\\s*(\\d+)\\s*周").matcher(input);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return resolveCurrentWeek(userId);
    }

    private Integer resolveRequestedWeekday(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        String text = input.replaceAll("\\s+", "");
        if (text.contains("今天")) {
            return toWeekdayCode(LocalDate.now());
        }
        if (text.contains("明天")) {
            return toWeekdayCode(LocalDate.now().plusDays(1));
        }
        if (text.contains("后天")) {
            return toWeekdayCode(LocalDate.now().plusDays(2));
        }
        if (text.contains("周一") || text.contains("星期一")) return 1;
        if (text.contains("周二") || text.contains("星期二")) return 2;
        if (text.contains("周三") || text.contains("星期三")) return 3;
        if (text.contains("周四") || text.contains("星期四")) return 4;
        if (text.contains("周五") || text.contains("星期五")) return 5;
        if (text.contains("周六") || text.contains("星期六")) return 6;
        if (text.contains("周日") || text.contains("周天") || text.contains("星期日") || text.contains("星期天")) return 7;
        return null;
    }

    private Integer resolveCurrentWeek(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getSemesterStart() == null) {
            return null;
        }
        int week = WeekCalculator.getCurrentWeek(user.getSemesterStart());
        return week > 0 ? week : null;
    }

    private Integer toWeekdayCode(LocalDate date) {
        int day = date.getDayOfWeek().getValue();
        return day >= 1 && day <= 7 ? day : null;
    }

    private Integer parseSessionStart(String sessions) {
        if (!StringUtils.hasText(sessions)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(sessions);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private Map<String, Object> courseScheduleResult(CourseSchedule schedule, Integer requestedWeek, Integer requestedWeekday) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "course_schedule");
        result.put("id", schedule.getId());
        result.put("name", schedule.getCourseName());
        result.put("teacherName", schedule.getTeacherName());
        result.put("location", schedule.getLocation());
        result.put("weekday", schedule.getWeekday());
        result.put("weekdayText", formatWeekday(schedule.getWeekday()));
        result.put("classSessions", schedule.getClassSessions());
        result.put("weekRange", schedule.getWeekRange());
        result.put("assessmentType", schedule.getAssessmentType());
        result.put("campus", schedule.getCampus());
        result.put("classCode", schedule.getClassCode());
        result.put("credit", schedule.getCredit());
        if (requestedWeek != null) {
            result.put("requestedWeek", requestedWeek);
        }
        if (requestedWeekday != null) {
            result.put("requestedWeekday", requestedWeekday);
            result.put("requestedWeekdayText", formatWeekday(requestedWeekday));
        }
        return result;
    }

    private String formatWeekday(Integer weekday) {
        if (weekday == null) {
            return "";
        }
        return switch (weekday) {
            case 1 -> "周一";
            case 2 -> "周二";
            case 3 -> "周三";
            case 4 -> "周四";
            case 5 -> "周五";
            case 6 -> "周六";
            case 7 -> "周日";
            default -> "";
        };
    }

    private Map<String, Object> restaurantResult(CampusFacility facility) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "restaurant");
        result.put("id", facility.getId());
        result.put("name", facility.getFacilityName());
        result.put("location", facility.getLocation());
        result.put("description", facility.getDescription());
        return result;
    }

    private Map<String, Object> stallResult(CanteenStall stall) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "stall");
        result.put("id", stall.getId());
        result.put("name", stall.getStallName());
        result.put("category", stall.getCategory());
        result.put("restaurantId", stall.getRestaurantId());
        result.put("score", stall.getScore());
        result.put("avgPrice", stall.getAvgPrice());
        return result;
    }

    private Map<String, Object> dishResult(Dish dish) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "dish");
        result.put("id", dish.getId());
        result.put("name", dish.getName());
        result.put("stallId", dish.getStallId());
        result.put("category", dish.getCategory());
        result.put("taste", dish.getTaste());
        result.put("rating", dish.getRating());
        result.put("price", dish.getPrice());
        return result;
    }

    private Map<String, Object> couponResult(PromotionCoupon coupon) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "coupon");
        result.put("id", coupon.getId());
        result.put("name", coupon.getCouponName());
        result.put("category", coupon.getCategory());
        result.put("tagType", coupon.getTagType());
        result.put("pickupLocation", coupon.getPickupLocation());
        result.put("description", coupon.getDescription());
        result.put("merchantName", resolveCouponMerchantName(coupon));
        result.put("stallName", resolveCouponStallName(coupon));
        result.put("facilityName", resolveCouponFacilityName(coupon));
        result.put("startDate", coupon.getStartDate());
        result.put("endDate", coupon.getEndDate());
        return result;
    }

    private void collectRelatedCouponsByStalls(Collection<Long> stallIds,
                                               List<Map<String, Object>> results,
                                               Set<Long> couponIds) {
        for (Long stallId : stallIds) {
            if (stallId == null) {
                continue;
            }
            promotionCouponRepository.findByStallIdAndStatus(stallId, 1).stream()
                    .filter(coupon -> couponIds.add(coupon.getId()))
                    .forEach(coupon -> results.add(couponResult(coupon)));
        }
    }

    private void collectRelatedCouponsByFacilities(Collection<Long> facilityIds,
                                                   List<Map<String, Object>> results,
                                                   Set<Long> couponIds) {
        for (Long facilityId : facilityIds) {
            if (facilityId == null) {
                continue;
            }
            promotionCouponRepository.findByFacilityIdAndStatus(facilityId, 1).stream()
                    .filter(coupon -> couponIds.add(coupon.getId()))
                    .forEach(coupon -> results.add(couponResult(coupon)));
        }
    }

    private String resolveCouponMerchantName(PromotionCoupon coupon) {
        if (coupon.getMerchantId() == null) {
            return null;
        }
        Merchant merchant = merchantRepository.findById(coupon.getMerchantId()).orElse(null);
        return merchant != null ? merchant.getMerchantName() : null;
    }

    private String resolveCouponStallName(PromotionCoupon coupon) {
        if (coupon.getStallId() == null) {
            return null;
        }
        CanteenStall stall = canteenStallRepository.findById(coupon.getStallId()).orElse(null);
        return stall != null ? stall.getStallName() : null;
    }

    private String resolveCouponFacilityName(PromotionCoupon coupon) {
        if (coupon.getFacilityId() == null) {
            return null;
        }
        CampusFacility facility = facilityRepository.findById(coupon.getFacilityId()).orElse(null);
        return facility != null ? facility.getFacilityName() : null;
    }
}
