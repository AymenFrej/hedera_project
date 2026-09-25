package com.hedera.agentplatform.accounts.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AccessConfiguration implements WebMvcConfigurer {
    private final ObjectProvider<AuthSessionService> sessions;
    public AccessConfiguration(ObjectProvider<AuthSessionService> sessions) { this.sessions = sessions; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                String method = request.getMethod();
                if (method.equals("OPTIONS") || path.equals("/api/v1/health") ||
                    (method.equals("POST") && (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register")))) return true;
                String role = sessions.getObject().require(request.getHeader("Authorization")).role;
                boolean manager = role.equals("ADMIN") || role.equals("PLATFORM");
                boolean allowed = path.startsWith("/api/v1/auth/") ||
                    path.equals("/api/v1/accounts") || path.equals("/api/v1/accounts/me") ||
                    (path.startsWith("/api/v1/admin/") && manager) ||
                    (path.startsWith("/api/v1/audit") && (manager || (role.equals("AUDITOR") && method.equals("GET")))) ||
                    (path.startsWith("/api/v1/assistant/") && (role.equals("USER") || role.equals("AUDITOR") || manager)) ||
                    ((path.startsWith("/api/v1/payments") || path.startsWith("/api/v1/tokens")) && (role.equals("USER") || role.equals("ADMIN"))) ||
                    ((path.startsWith("/api/v1/policies") || path.startsWith("/api/v1/approvals")) && manager);
                if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your role cannot use this feature");
                return true;
            }
        }).addPathPatterns("/api/v1/**");
    }
}
