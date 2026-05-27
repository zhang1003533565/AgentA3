package com.example.appbackend.websocket;

import com.example.appbackend.util.JwtUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Component
public class MeetingAsrHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    public MeetingAsrHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token) || !jwtUtil.validateToken(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put("userId", jwtUtil.getUserIdFromToken(token));
        attributes.put("username", jwtUtil.getUsernameFromToken(token));
        attributes.put("token", token);
        attributes.put("authorization", "Bearer " + token);
        attributes.put("sessionId", resolveSessionId(request));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private String resolveToken(ServerHttpRequest request) {
        List<String> authorization = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isEmpty()) {
            String header = authorization.get(0);
            if (header != null && header.startsWith("Bearer ")) {
                return header.substring(7);
            }
        }
        return UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
    }

    private String resolveSessionId(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        String marker = "/api/meetings/";
        int start = path.indexOf(marker);
        int end = path.indexOf("/asr/stream");
        if (start < 0 || end <= start) {
            return "";
        }
        return path.substring(start + marker.length(), end);
    }
}
