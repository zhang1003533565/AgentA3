package com.example.appbackend.websocket;

import com.example.appbackend.service.MeetingAsrRecordService;
import com.example.appbackend.service.SystemConfigService;
import com.example.appbackend.entity.User;
import com.example.appbackend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class MeetingAsrWebSocketHandler extends TextWebSocketHandler {

    private static final String XFYUN_DEFAULT_WEBSOCKET_URL = "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1";
    private static final String XFYUN_DEFAULT_LANG = "autodialect";
    private static final String XFYUN_DEFAULT_AUDIO_ENCODE = "pcm_s16le";
    private static final String XFYUN_DEFAULT_SAMPLE_RATE = "16000";
    private final ObjectMapper objectMapper;
    private final MeetingAsrRecordService recordService;
    private final SystemConfigService systemConfigService;
    private final UserRepository userRepository;
    private final HttpClient httpClient;
    private final Map<String, AsrBridge> bridges = new ConcurrentHashMap<>();
    private final Map<String, java.util.Set<WebSocketSession>> meetingSessions = new ConcurrentHashMap<>();
    /** 会议级 Agent 1 实时摘要缓存：meetingSessionId → 已完成的摘要列表，供中途入会成员获取历史总结 */
    private final Map<String, List<Map<String, Object>>> meetingSummaryCache = new ConcurrentHashMap<>();
    private static final int AUDIO_FRAME_SIZE = 1280;
    private static final int AUDIO_FRAME_INTERVAL_MS = 40;

    public MeetingAsrWebSocketHandler(ObjectMapper objectMapper,
                                      MeetingAsrRecordService recordService,
                                      SystemConfigService systemConfigService,
                                      UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.recordService = recordService;
        this.systemConfigService = systemConfigService;
        this.userRepository = userRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        AsrBridge bridge = new AsrBridge(session);
        bridges.put(session.getId(), bridge);
        String meetingSessionId = meetingSessionId(session);
        if (!StringUtils.hasText(meetingSessionId)) {
            // 兜底：从 URI 路径提取会议 sessionId，避免 handshake attributes 丢失导致无法分组
            meetingSessionId = resolveSessionIdFromUri(session.getUri());
            if (StringUtils.hasText(meetingSessionId)) {
                session.getAttributes().put("sessionId", meetingSessionId);
            }
        }
        System.out.println("[ASR-Connect] session=" + session.getId()
                + " meetingSessionId=" + meetingSessionId
                + " uri=" + session.getUri() + " attrs=" + session.getAttributes());
        if (StringUtils.hasText(meetingSessionId)) {
            java.util.Set<WebSocketSession> sessions = meetingSessions.computeIfAbsent(meetingSessionId, key -> ConcurrentHashMap.newKeySet());
            boolean added = sessions.add(session);
            System.out.println("[ASR-Connect] joined " + meetingSessionId
                    + " online=" + sessions.size() + " added=" + added);
            if (!added) {
                System.out.println("[ASR-Connect] WARNING: session already in group!");
            }
        } else {
            System.out.println("[ASR-Connect] meetingSessionId empty, not joined to any group");
        }
        // 新连接建立后，如果有历史 AI 实时摘要则主动推送给该客户端，支持中途入会查看完整历史总结
        sendAiSummaryHistory(session, meetingSessionId);
        bridge.connect();
    }

    /**
     * 向新连接的客户端推送该会议已有的 Agent 1 实时摘要历史。
     * 中途加入的成员可通过此机制获取从会议开始到当前时刻已产生的全部 AI 实时总结。
     */
    private void sendAiSummaryHistory(WebSocketSession session, String meetingSessionId) {
        if (!StringUtils.hasText(meetingSessionId)) return;
        List<Map<String, Object>> history = meetingSummaryCache.get(meetingSessionId);
        if (history == null || history.isEmpty()) return;
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "ai_summary_history");
        msg.put("items", new ArrayList<>(history));
        sendToSession(session, msg);
        System.out.println("[AI-Summary-History] sent " + history.size() + " items to session=" + session.getId() + " meeting=" + meetingSessionId);
    }

    /**
     * 处理客户端上报的 Agent 1 实时摘要完成消息。
     * 将摘要存入会议级缓存并广播给该会议所有其他在线客户端。
     */
    private void handleAiSummaryMessage(WebSocketSession session, JsonNode node) {
        String meetingSessionId = meetingSessionId(session);
        if (!StringUtils.hasText(meetingSessionId)) return;
        String id = node.path("id").asText("");
        String text = node.path("text").asText("");
        String time = node.path("time").asText("");
        if (!StringUtils.hasText(id) || !StringUtils.hasText(text)) return;
        // 存入会议级缓存，去重防止同一条摘要重复存储
        List<Map<String, Object>> cache = meetingSummaryCache.computeIfAbsent(meetingSessionId, k -> new ArrayList<>());
        synchronized (cache) {
            boolean exists = cache.stream().anyMatch(item -> id.equals(item.get("id")));
            if (!exists) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", id);
                item.put("text", text);
                item.put("time", time);
                cache.add(item);
                // 限制缓存大小，防止超长会议内存溢出
                if (cache.size() > 50) {
                    cache.subList(0, cache.size() - 50).clear();
                }
            }
        }
        // 广播给该会议所有在线客户端（含发送者，由前端负责去重）
        Map<String, Object> broadcast = new HashMap<>();
        broadcast.put("type", "ai_summary");
        broadcast.put("id", id);
        broadcast.put("text", text);
        broadcast.put("time", time);
        broadcastToMeeting(session, broadcast);
        System.out.println("[AI-Summary-Broadcast] id=" + id + " meeting=" + meetingSessionId + " cacheSize=" + cache.size());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        AsrBridge bridge = bridges.get(session.getId());
        if (bridge != null) {
            bridge.sendAudio(message.getPayload());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        AsrBridge bridge = bridges.get(session.getId());
        if (bridge == null) {
            return;
        }
        String payload = message.getPayload();
        if (!StringUtils.hasText(payload)) {
            return;
        }
        // 弹幕消息：转发广播给该会议所有在线成员，实现跨账号实时同步
        if (payload.contains("\"type\":\"danmaku\"")) {
            try {
                JsonNode node = objectMapper.readTree(payload);
                if ("danmaku".equals(node.path("type").asText())) {
                    broadcastToMeeting(session, Map.of(
                            "type", "danmaku",
                            "speakerUserId", speakerUserId(session),
                            "speaker", node.path("speaker").asText(speakerName(session)),
                            "text", node.path("text").asText("")
                    ));
                }
            } catch (Exception ignored) {
            }
            return;
        }
        // AI 实时摘要完成消息：客户端完成 Agent 1 摘要生成后上报，由后端存储并广播给会议所有成员
        if (payload.contains("\"type\":\"ai_summary\"") && !payload.contains("\"type\":\"ai_summary_history\"")) {
            try {
                JsonNode node = objectMapper.readTree(payload);
                if ("ai_summary".equals(node.path("type").asText())) {
                    handleAiSummaryMessage(session, node);
                }
            } catch (Exception ignored) {
            }
            return;
        }
        // 显式控制消息：只接受{"stop":true}，不再匹配 is_speaking=false 以防误伤
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("stop") && node.get("stop").asBoolean()) {
                bridge.stopAudioStream("client_stop");
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        closeBridge(session, "error");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeBridge(session, "closed");
    }

    private void closeBridge(WebSocketSession session, String reason) {
        AsrBridge bridge = bridges.remove(session.getId());
        String meetingSessionId = meetingSessionId(session);
        if (StringUtils.hasText(meetingSessionId)) {
            java.util.Set<WebSocketSession> sessions = meetingSessions.get(meetingSessionId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    meetingSessions.remove(meetingSessionId);
                }
            }
        }
        if (bridge != null) {
            bridge.close(reason);
        }
    }

    private String meetingSessionId(WebSocketSession session) {
        Object sessionId = session.getAttributes().get("sessionId");
        return sessionId instanceof String value ? value : "";
    }

    private String resolveSessionIdFromUri(java.net.URI uri) {
        if (uri == null) {
            return "";
        }
        String path = uri.getPath();
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String marker = "/api/meetings/";
        int start = path.indexOf(marker);
        int end = path.indexOf("/asr/stream");
        if (start < 0 || end <= start) {
            return "";
        }
        return path.substring(start + marker.length(), end);
    }

    private String speakerName(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        if (userId instanceof Long id) {
            return userRepository.findById(id)
                    .map(this::speakerName)
                    .orElseGet(() -> fallbackSpeakerName(session));
        }
        return fallbackSpeakerName(session);
    }

    private Object speakerUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        return userId == null ? "" : userId;
    }

    private String speakerName(User user) {
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName().trim();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        if (StringUtils.hasText(user.getPersonalNumber())) {
            return user.getPersonalNumber().trim();
        }
        return "参会成员";
    }

    private String fallbackSpeakerName(WebSocketSession session) {
        Object username = session.getAttributes().get("username");
        return username instanceof String value && StringUtils.hasText(value) ? value : "参会成员";
    }

    private void broadcastToMeeting(WebSocketSession sourceSession, Map<String, Object> payload) {
        String meetingSessionId = meetingSessionId(sourceSession);
        System.out.println("[ASR-Broadcast] type=" + payload.get("type") + " meeting=" + meetingSessionId);
        if (!StringUtils.hasText(meetingSessionId)) {
            sendToSession(sourceSession, payload);
            return;
        }
        java.util.Set<WebSocketSession> sessions = meetingSessions.get(meetingSessionId);
        System.out.println("[ASR-Broadcast] 在线数=" + (sessions == null ? 0 : sessions.size()));
        if (sessions == null || sessions.isEmpty()) {
            sendToSession(sourceSession, payload);
            return;
        }
        sessions.forEach(session -> sendToSession(session, payload));
    }

    private void sendToSession(WebSocketSession session, Map<String, Object> payload) {
        if (session == null || !session.isOpen()) {
            System.out.println("[ASR-Send] 跳过: session=" + (session == null ? "null" : "closed") + " type=" + payload.get("type"));
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            System.out.println("[ASR-Send] 发送失败 type=" + payload.get("type") + " 异常=" + e);
        }
    }

    private String buildXfyunUri(String sessionId) {
        String xfyunWebSocketUrl = getAsrConfig("websocket-url");
        String xfyunAppId = getAsrConfig("app-id");
        String accessKeyId = getAsrConfig("access-key-id");
        String accessKeySecret = getAsrConfig("access-key-secret");
        String lang = getAsrConfig("lang");
        String audioEncode = getAsrConfig("audio-encode");
        String sampleRate = getAsrConfig("samplerate");
        List<String> missingFields = new ArrayList<>();
        if (!StringUtils.hasText(xfyunWebSocketUrl)) missingFields.add("websocket-url");
        if (!StringUtils.hasText(xfyunAppId)) missingFields.add("app-id");
        if (!StringUtils.hasText(accessKeyId)) missingFields.add("access-key-id");
        if (!StringUtils.hasText(accessKeySecret)) missingFields.add("access-key-secret");
        if (!StringUtils.hasText(lang)) missingFields.add("lang");
        if (!StringUtils.hasText(audioEncode)) missingFields.add("audio-encode");
        if (!StringUtils.hasText(sampleRate)) missingFields.add("samplerate");
        if (!missingFields.isEmpty()) {
            throw new IllegalStateException("请在 Java 后台语音转写配置中维护 ai.asr.xfyun." + String.join("、ai.asr.xfyun.", missingFields));
        }
        TreeMap<String, String> params = new TreeMap<>();
        params.put("accessKeyId", accessKeyId);
        params.put("appId", xfyunAppId);
        params.put("audio_encode", audioEncode);
        params.put("lang", lang);
        params.put("samplerate", sampleRate);
        params.put("utc", OffsetDateTime.now(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")));
        params.put("uuid", sessionId);

        String baseString = buildQuery(params);
        String signature = buildSignature(baseString, accessKeySecret);
        return trimTrailingSlash(xfyunWebSocketUrl) + "?" + baseString + "&signature=" + encode(signature);
    }

    private String getAsrConfig(String field) {
        return systemConfigService.getValue("ai.asr.xfyun." + field, defaultAsrConfig(field)).trim();
    }

    private String defaultAsrConfig(String field) {
        return switch (field) {
            case "websocket-url" -> XFYUN_DEFAULT_WEBSOCKET_URL;
            case "lang" -> XFYUN_DEFAULT_LANG;
            case "audio-encode" -> XFYUN_DEFAULT_AUDIO_ENCODE;
            case "samplerate" -> XFYUN_DEFAULT_SAMPLE_RATE;
            default -> "";
        };
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
            throw new IllegalStateException("讯飞实时转写大模型签名生成失败: " + e.getMessage(), e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private class AsrBridge {
        private final WebSocketSession clientSession;
        private final Queue<ByteBuffer> audioFrames = new ConcurrentLinkedQueue<>();
        private final StringBuilder partialText = new StringBuilder();
        private final TreeMap<Integer, String> finalSegments = new TreeMap<>();
        private final ScheduledExecutorService audioSender = Executors.newSingleThreadScheduledExecutor();
        private byte[] pendingAudio = new byte[0];
        private int fallbackSegmentId = 0;
        private WebSocket xfyunSocket;
        private volatile boolean xfyunOpen = false;
        private volatile boolean xfyunConnecting = false;
        private volatile boolean saved = false;
        private volatile boolean audioSenderStarted = false;
        private volatile boolean audioStreamStopped = false;
        private volatile boolean bridgeClosed = false;
        private volatile String serviceSessionId;
        private String requestUuid = newRequestUuid();

        AsrBridge(WebSocketSession clientSession) {
            this.clientSession = clientSession;
        }

        void connect() {
            if (bridgeClosed || xfyunOpen || xfyunConnecting) {
                return;
            }
            xfyunConnecting = true;
            try {
                httpClient.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .buildAsync(URI.create(buildXfyunUri(requestUuid)), new XfyunListener(this))
                        .orTimeout(8, TimeUnit.SECONDS)
                        .whenComplete((socket, error) -> {
                            xfyunConnecting = false;
                            if (error != null) {
                                // 讯飞连接失败仅通知字幕不可用，不关闭 WebSocket，保留弹幕等轻量消息通道
                                sendClient(Map.of("type", "asr_error", "message", "讯飞实时转写连接失败: " + error.getMessage()));
                                return;
                            }
                            xfyunSocket = socket;
                            xfyunOpen = true;
                            audioStreamStopped = false;
                            startAudioSender();
                            sendClient(Map.of("type", "asr_ready"));
                        });
            } catch (Exception e) {
                xfyunConnecting = false;
                // 讯飞启动失败仅通知字幕不可用，不关闭 WebSocket，保留弹幕等轻量消息通道
                sendClient(Map.of("type", "asr_error", "message", "讯飞实时转写启动失败: " + e.getMessage()));
            }
        }

        private String newRequestUuid() {
            return UUID.randomUUID().toString().replace("-", "");
        }

        synchronized void sendAudio(ByteBuffer payload) {
            if (payload == null || !payload.hasRemaining() || bridgeClosed) {
                return;
            }
            ensureAudioStreamReady();
            byte[] incoming = new byte[payload.remaining()];
            payload.get(incoming);
            byte[] merged = new byte[pendingAudio.length + incoming.length];
            System.arraycopy(pendingAudio, 0, merged, 0, pendingAudio.length);
            System.arraycopy(incoming, 0, merged, pendingAudio.length, incoming.length);

            int offset = 0;
            while (offset + AUDIO_FRAME_SIZE <= merged.length) {
                audioFrames.offer(ByteBuffer.wrap(Arrays.copyOfRange(merged, offset, offset + AUDIO_FRAME_SIZE)));
                offset += AUDIO_FRAME_SIZE;
            }
            pendingAudio = Arrays.copyOfRange(merged, offset, merged.length);
        }

        void finish() {
            stopAudioStream("client_stop");
            closeClient(CloseStatus.NORMAL);
        }

        synchronized void stopAudioStream(String reason) {
            if (bridgeClosed || audioStreamStopped) {
                return;
            }
            drainQueuedAudio();
            sendEndFrame();
            saveTranscript();
            WebSocket socket = xfyunSocket;
            xfyunOpen = false;
            xfyunConnecting = false;
            xfyunSocket = null;
            audioStreamStopped = true;
            if (socket != null) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, reason);
            }
        }

        void close(String reason) {
            stopAudioStream(reason);
            bridgeClosed = true;
            audioSender.shutdownNow();
        }

        private synchronized void ensureAudioStreamReady() {
            if (xfyunOpen || xfyunConnecting) {
                return;
            }
            if (audioStreamStopped || xfyunSocket == null) {
                requestUuid = newRequestUuid();
                serviceSessionId = null;
                saved = false;
                fallbackSegmentId = 0;
                partialText.setLength(0);
                finalSegments.clear();
                pendingAudio = new byte[0];
                audioFrames.clear();
                audioStreamStopped = false;
                connect();
            }
        }

        void onXfyunText(String payload) {
            try {
                JsonNode node = objectMapper.readTree(payload);
                String action = node.path("action").asText(node.path("msg_type").asText(""));
                String code = node.path("code").asText("0");
                if (!"0".equals(code)) {
                    String message = node.path("desc").asText(node.path("message").asText("讯飞实时转写返回错误"));
                    sendClient(Map.of("type", "asr_error", "message", message));
                    return;
                }
                if ("started".equals(action)) {
                    sendClient(Map.of("type", "asr_ready"));
                    return;
                }
                JsonNode data = node.path("data");
                if (data.has("sessionId")) {
                    serviceSessionId = data.path("sessionId").asText();
                }
                if ("result".equals(action)) {
                    JsonNode dataNode = normalizeDataNode(data);
                    String text = extractTextFromData(dataNode);
                    boolean isFinal = isFinalResult(dataNode);
                    if (StringUtils.hasText(text)) {
                        if (isFinal) {
                            finalSegments.put(resolveSegmentId(dataNode), text);
                            partialText.setLength(0);
                        } else {
                            partialText.setLength(0);
                            partialText.append(text);
                        }
                    }
                    sendClient(Map.of(
                            "type", "asr_result",
                            "speakerUserId", speakerUserId(clientSession),
                            "speaker", speakerName(clientSession),
                            "text", text,
                            "mode", "xfyun_rtasr_llm",
                            "isFinal", isFinal,
                            "transcript", isFinal ? buildTranscript() : buildTranscriptWithPartial()
                    ));
                    return;
                }
                if ("error".equals(action)) {
                    sendClient(Map.of("type", "asr_error", "message", node.path("desc").asText("讯飞实时转写异常")));
                }
            } catch (Exception e) {
                sendClient(Map.of("type", "asr_raw", "payload", payload));
            }
        }

        private JsonNode normalizeDataNode(JsonNode data) throws Exception {
            if (data == null || data.isMissingNode() || data.isNull()) {
                return objectMapper.createObjectNode();
            }
            return data.isTextual() ? objectMapper.readTree(data.asText()) : data;
        }

        private boolean isFinalResult(JsonNode dataNode) {
            JsonNode st = dataNode.path("cn").path("st");
            if (st.has("type")) {
                return "0".equals(st.path("type").asText("0"));
            }
            if (dataNode.has("is_final")) {
                return dataNode.path("is_final").asBoolean(false);
            }
            return true;
        }

        private int resolveSegmentId(JsonNode dataNode) {
            JsonNode st = dataNode.path("cn").path("st");
            if (st.has("bg") && st.path("bg").canConvertToInt()) {
                return st.path("bg").asInt();
            }
            if (dataNode.has("seg_id") && dataNode.path("seg_id").canConvertToInt()) {
                return dataNode.path("seg_id").asInt();
            }
            return fallbackSegmentId++;
        }

        private String extractTextFromData(JsonNode dataNode) {
            List<String> words = new ArrayList<>();
            collectWords(dataNode, words);
            return String.join("", words);
        }

        private void collectWords(JsonNode node, List<String> words) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return;
            }
            if (node.isObject() && node.has("w") && node.get("w").isTextual()) {
                String word = node.get("w").asText();
                if (StringUtils.hasText(word)) {
                    words.add(word);
                }
            }
            if (node.isContainerNode()) {
                node.elements().forEachRemaining(child -> collectWords(child, words));
            }
        }

        private void sendEndFrame() {
            WebSocket socket = xfyunSocket;
            if (socket == null) {
                return;
            }
            try {
                String sessionId = StringUtils.hasText(serviceSessionId) ? serviceSessionId : requestUuid;
                socket.sendText(objectMapper.writeValueAsString(Map.of("end", true, "sessionId", sessionId)), true);
            } catch (Exception ignored) {
            }
        }

        private void startAudioSender() {
            if (audioSenderStarted) {
                return;
            }
            audioSenderStarted = true;
            audioSender.scheduleAtFixedRate(this::sendNextAudioFrame, 0, AUDIO_FRAME_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }

        private void sendNextAudioFrame() {
            WebSocket socket = xfyunSocket;
            if (!xfyunOpen || socket == null) {
                return;
            }
            ByteBuffer frame = audioFrames.poll();
            if (frame != null) {
                socket.sendBinary(frame, true);
            }
        }

        private void drainQueuedAudio() {
            WebSocket socket = xfyunSocket;
            if (!xfyunOpen || socket == null) {
                return;
            }
            flushPendingAudioFrame();
            ByteBuffer frame;
            while ((frame = audioFrames.poll()) != null) {
                socket.sendBinary(frame, true);
                try {
                    Thread.sleep(AUDIO_FRAME_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private synchronized void flushPendingAudioFrame() {
            if (pendingAudio.length == 0) {
                return;
            }
            byte[] padded = new byte[AUDIO_FRAME_SIZE];
            System.arraycopy(pendingAudio, 0, padded, 0, pendingAudio.length);
            audioFrames.offer(ByteBuffer.wrap(padded));
            pendingAudio = new byte[0];
        }

        private String buildTranscript() {
            String speaker = speakerName(clientSession);
            List<String> segmentsWithSpeaker = new ArrayList<>();
            for (String text : finalSegments.values()) {
                if (StringUtils.hasText(text)) {
                    segmentsWithSpeaker.add("[说话人：" + speaker + "] " + text.trim());
                }
            }
            return String.join("\n", segmentsWithSpeaker);
        }

        private String buildTranscriptWithPartial() {
            return buildTranscript() + partialText;
        }

        private void saveTranscript() {
            if (saved) {
                return;
            }
            saved = true;
            String transcript = buildTranscript().trim();
            if (!StringUtils.hasText(transcript)) {
                transcript = partialText.toString().trim();
            }
            if (!StringUtils.hasText(transcript)) {
                return;
            }
            Long userId = (Long) clientSession.getAttributes().get("userId");
            String sessionId = (String) clientSession.getAttributes().get("sessionId");
            recordService.saveFinalTranscript(userId, sessionId, transcript);
            sendClient(Map.of(
                    "type", "asr_saved",
                    "speakerUserId", speakerUserId(clientSession),
                    "speaker", speakerName(clientSession),
                    "transcript", transcript
            ));
        }

        void onXfyunClosed(WebSocket socket) {
            if (xfyunSocket == socket) {
                xfyunOpen = false;
                xfyunConnecting = false;
                xfyunSocket = null;
            }
            saveTranscript();
        }

        private void sendClient(Map<String, Object> payload) {
            Object type = payload.get("type");
            System.out.println("[ASR-SendClient] type=" + type + " speaker=" + speakerName(clientSession));
            if ("asr_result".equals(type) || "asr_saved".equals(type)) {
                broadcastToMeeting(clientSession, payload);
                return;
            }
            sendToSession(clientSession, payload);
        }

        private void closeClient(CloseStatus status) {
            try {
                if (clientSession.isOpen()) {
                    clientSession.close(status);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static class XfyunListener implements WebSocket.Listener {
        private final AsrBridge bridge;
        private final StringBuilder textBuffer = new StringBuilder();

        XfyunListener(AsrBridge bridge) {
            this.bridge = bridge;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                bridge.onXfyunText(textBuffer.toString());
                textBuffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            bridge.onXfyunClosed(webSocket);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            bridge.sendClient(Map.of("type", "asr_error", "message", "讯飞实时转写服务异常: " + error.getMessage()));
            bridge.saveTranscript();
        }
    }
}
