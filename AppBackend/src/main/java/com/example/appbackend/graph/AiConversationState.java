package com.example.appbackend.graph;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;

public class AiConversationState extends AgentState {

    public static final String SESSION_ID = "sessionId";
    public static final String SESSION_TOKEN = "sessionToken";
    public static final String USER_ID = "userId";
    public static final String MODEL = "model";
    public static final String PROMPT = "prompt";
    public static final String INPUT = "input";
    public static final String HISTORY = "history";
    public static final String SEARCH_KEYWORD = "searchKeyword";
    public static final String SEARCH_RESULTS = "searchResults";
    public static final String REQUEST_MESSAGES = "requestMessages";
    public static final String ANSWER = "answer";

    public AiConversationState(Map<String, Object> initData) {
        super(initData);
    }

    public String sessionId() {
        return value(SESSION_ID).map(String.class::cast).orElse(null);
    }

    public String sessionToken() {
        return value(SESSION_TOKEN).map(String.class::cast).orElse(null);
    }

    public Long userId() {
        return value(USER_ID).map(v -> ((Number) v).longValue()).orElse(null);
    }

    public String model() {
        return value(MODEL).map(String.class::cast).orElse(null);
    }

    public String prompt() {
        return value(PROMPT).map(String.class::cast).orElse(null);
    }

    public String input() {
        return value(INPUT).map(String.class::cast).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> history() {
        return value(HISTORY).map(v -> (List<Map<String, String>>) v).orElse(List.of());
    }

    public String searchKeyword() {
        return value(SEARCH_KEYWORD).map(String.class::cast).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchResults() {
        return value(SEARCH_RESULTS).map(v -> (List<Map<String, Object>>) v).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> requestMessages() {
        return value(REQUEST_MESSAGES).map(v -> (List<Map<String, String>>) v).orElse(List.of());
    }

    public String answer() {
        return value(ANSWER).map(String.class::cast).orElse(null);
    }
}
