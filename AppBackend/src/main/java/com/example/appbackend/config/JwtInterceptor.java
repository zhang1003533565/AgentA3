package com.example.appbackend.config;

import com.example.appbackend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS 预检请求不带 Authorization，必须放行，否则预检失败会导致跨域请求被浏览器拦截
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");

        // 校园地图点位只读接口允许未登录预览，便于前端直接渲染后端标点
        if ("GET".equalsIgnoreCase(request.getMethod()) && isPublicMapPlaceRead(request.getRequestURI())) {
            if (token != null && token.startsWith("Bearer ")) {
                String raw = token.substring(7);
                if (jwtUtil.validateToken(raw)) {
                    request.setAttribute("username", jwtUtil.getUsernameFromToken(raw));
                    request.setAttribute("role", jwtUtil.getRoleFromToken(raw));
                    request.setAttribute("userId", jwtUtil.getUserIdFromToken(raw));
                }
            }
            return true;
        }

        if (token == null || !token.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或Token无效\",\"data\":null}");
            return false;
        }

        token = token.substring(7);

        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token已过期\",\"data\":null}");
            return false;
        }

        String username = jwtUtil.getUsernameFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);
        request.setAttribute("username", username);
        request.setAttribute("role", role);
        request.setAttribute("userId", userId);

        return true;
    }

    private boolean isPublicMapPlaceRead(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) return false;
        String path = requestUri;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) path = path.substring(0, queryIndex);
        if ("/api/v1/map-places".equals(path)) return true;
        if (path.matches("^/api/v1/map-places/\\d+$")) return true;
        if (path.matches("^/api/v1/map-places/\\d+/fence$")) return true;
        if (path.matches("^/api/v1/map-places/\\d+/images$")) return true;
        if (path.matches("^/api/v1/map-places/floors/\\d+/plan$")) return true;
        return path.matches("^/api/v1/map-places/floor-plans/\\d+/positions$");
    }
}
