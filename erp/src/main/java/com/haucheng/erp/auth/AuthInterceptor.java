package com.haucheng.erp.auth;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.service.AuthService;
import com.haucheng.erp.vo.CurrentUserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        CurrentUserContext.clear();
        if (!(handler instanceof HandlerMethod method)) return true;
        RequirePermission requirement = method.getMethodAnnotation(RequirePermission.class);
        if (requirement == null) requirement = method.getBeanType().getAnnotation(RequirePermission.class);
        if (requirement == null) return true;
        String token = authService.extractBearerToken(request.getHeader("Authorization"));
        if (token == null) { writeError(response, 401, 40100, "请先登录"); return false; }
        CurrentUserView user = authService.authenticate(token);
        if (user == null) { writeError(response, 401, 40100, "登录已过期，请重新登录"); return false; }
        if (requirement.value().length > 0 && Arrays.stream(requirement.value()).noneMatch(user.getPermissions()::contains)) {
            writeError(response, 403, 40300, "没有访问该接口的权限"); return false;
        }
        CurrentUserContext.set(user);
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) { CurrentUserContext.clear(); }
    private void writeError(HttpServletResponse response, int status, int code, String message) throws IOException {
        response.setStatus(status); response.setCharacterEncoding(StandardCharsets.UTF_8.name()); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
    }
}
