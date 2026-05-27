package com.example.appbackend.websocket;

import com.example.appbackend.service.MeetingAsrRecordService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Component
public class MeetingAsrWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final MeetingAsrRecordService recordService;
    private final HttpClient httpClient;
    private final Map<String, AsrBridge> bridges = new ConcurrentHashMap<>();

    @Value("${ai.asr.funasr.websocket-url:ws://localhost:10095}")
    private String funasrWebSocketUrl;

    @Value("${ai.asr.funasr.mode:2pass}")
    private String mode;

    @Value("${ai.asr.funasr.chunk-size:5,10,5}")
    private String chunkSize;

    @Value("${ai.asr.funasr.chunk-interval:10}")
    private Integer chunkInterval;

    @Value("${ai.asr.funasr.sample-rate:16000}")
    private Integer sampleRate;

    public MeetingAsrWebSocketHandler(ObjectMapper objectMapper, MeetingAsrRecordService recordService) {
        this.objectMapper = objectMapper;
        this.recordService = recordService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        AsrBridge bridge = new AsrBridge(session);
        bridges.put(session.getId(), bridge);
        bridge.connect();
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
        if (payload.contains("\"stop\"") || payload.contains("\"is_speaking\":false")) {
            bridge.finish();
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
        if (bridge != null) {
            bridge.close(reason);
        }
    }

    private class AsrBridge {
        private final WebSocketSession clientSession;
        private final Queue<ByteBuffer> pendingAudio = new ConcurrentLinkedQueue<>();
        private final StringBuilder partialText = new StringBuilder();
        private final List<String> finalSegments = new ArrayList<>();
        private volatile WebSocket funasrSocket;
        private volatile boolean funasrOpen = false;
        private volatile boolean saved = false;

        AsrBridge(WebSocketSession clientSession) {
            this.clientSession = clientSession;
        }

        void connect() {
            try {
                httpClient.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .buildAsync(URI.create(funasrWebSocketUrl), new FunasrListener(this))
                        .orTimeout(8, TimeUnit.SECONDS)
                        .whenComplete((socket, error) -> {
                            if (error != null) {
                                sendClient(Map.of("type", "asr_error", "message", "FunASR 连接失败: " + error.getMessage()));
                                closeClient(CloseStatus.SERVER_ERROR);
                                return;
                            }
                            funasrSocket = socket;
                            funasrOpen = true;
                            sendStartFrame();
                            flushPendingAudio();
                            sendClient(Map.of("type", "asr_ready"));
                        });
            } catch (Exception e) {
                sendClient(Map.of("type", "asr_error", "message", "FunASR 连接失败: " + e.getMessage()));
                closeClient(CloseStatus.SERVER_ERROR);
            }
        }

        void sendAudio(ByteBuffer payload) {
            ByteBuffer copy = ByteBuffer.allocate(payload.remaining());
            copy.put(payload);
            copy.flip();
            if (!funasrOpen || funasrSocket == null) {
                pendingAudio.offer(copy);
                return;
            }
            funasrSocket.sendBinary(copy, true);
        }

        void finish() {
            sendFinishFrame();
            saveTranscript();
            close("client_stop");
            closeClient(CloseStatus.NORMAL);
        }

        void close(String reason) {
            sendFinishFrame();
            saveTranscript();
            WebSocket socket = funasrSocket;
            if (socket != null) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, reason);
            }
        }

        void onFunasrText(String payload) {
            try {
                JsonNode node = objectMapper.readTree(payload);
                String text = node.path("text").asText("");
                String responseMode = node.path("mode").asText("");
                boolean isFinal = node.path("is_final").asBoolean(false)
                        || "2pass-offline".equals(responseMode)
                        || "offline".equals(responseMode);
                if (StringUtils.hasText(text)) {
                    if (isFinal) {
                        finalSegments.add(text.trim());
                    } else {
                        partialText.setLength(0);
                        partialText.append(text.trim());
                    }
                }
                sendClient(Map.of(
                        "type", "asr_result",
                        "text", text,
                        "mode", responseMode,
                        "isFinal", isFinal,
                        "transcript", buildTranscript()
                ));
            } catch (Exception e) {
                sendClient(Map.of("type", "asr_raw", "payload", payload));
            }
        }

        private void sendStartFrame() {
            sendFunasrText(Map.of(
                    "mode", mode,
                    "chunk_size", parseChunkSize(),
                    "chunk_interval", chunkInterval,
                    "wav_name", "meeting-" + clientSession.getId(),
                    "is_speaking", true,
                    "audio_fs", sampleRate,
                    "itn", true
            ));
        }

        private void sendFinishFrame() {
            sendFunasrText(Map.of("is_speaking", false));
        }

        private void sendFunasrText(Map<String, Object> payload) {
            WebSocket socket = funasrSocket;
            if (socket == null) {
                return;
            }
            try {
                socket.sendText(objectMapper.writeValueAsString(payload), true);
            } catch (Exception ignored) {
            }
        }

        private void flushPendingAudio() {
            ByteBuffer chunk;
            while ((chunk = pendingAudio.poll()) != null) {
                sendAudio(chunk);
            }
        }

        private List<Integer> parseChunkSize() {
            return Arrays.stream(chunkSize.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(Integer::parseInt)
                    .toList();
        }

        private String buildTranscript() {
            String finalText = String.join("", finalSegments);
            if (partialText.length() == 0) {
                return finalText;
            }
            return finalText + partialText;
        }

        private void saveTranscript() {
            if (saved) {
                return;
            }
            saved = true;
            String transcript = String.join("", finalSegments).trim();
            if (!StringUtils.hasText(transcript)) {
                transcript = partialText.toString().trim();
            }
            if (!StringUtils.hasText(transcript)) {
                return;
            }
            Long userId = (Long) clientSession.getAttributes().get("userId");
            String sessionId = (String) clientSession.getAttributes().get("sessionId");
            recordService.saveFinalTranscript(userId, sessionId, transcript);
            sendClient(Map.of("type", "asr_saved", "transcript", transcript));
        }

        private void sendClient(Map<String, Object> payload) {
            if (!clientSession.isOpen()) {
                return;
            }
            try {
                synchronized (clientSession) {
                    clientSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                }
            } catch (Exception ignored) {
            }
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

    private static class FunasrListener implements WebSocket.Listener {
        private final AsrBridge bridge;
        private final StringBuilder textBuffer = new StringBuilder();

        FunasrListener(AsrBridge bridge) {
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
                bridge.onFunasrText(textBuffer.toString());
                textBuffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            bridge.saveTranscript();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            bridge.sendClient(Map.of("type", "asr_error", "message", "FunASR 服务异常: " + error.getMessage()));
            bridge.saveTranscript();
        }
    }
}
