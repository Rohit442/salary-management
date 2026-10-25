package com.acme.salarymanagement.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SessionUtility {
    private final int configuredTimeout;
    private final boolean secure;
    private final String path;
    private final String domain;

    public SessionUtility(@Value("${app.auth.idle-timeout-seconds:1800}") int configuredTimeout,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
            @Value("${server.servlet.session.cookie.path:/}") String path,
            @Value("${server.servlet.session.cookie.domain:}") String domain) {
        if (configuredTimeout < 1) throw new IllegalArgumentException("Session timeout must be positive");
        this.configuredTimeout = configuredTimeout;
        this.secure = secure;
        this.path = path;
        this.domain = domain;
    }

    public int idleTimeoutSeconds(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return configuredTimeout;
        if (session.getMaxInactiveInterval() <= 0) session.setMaxInactiveInterval(configuredTimeout);
        return session.getMaxInactiveInterval();
    }

    public void invalidate(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    public void expireCookie(HttpServletRequest request, HttpServletResponse response) {
        var cookie = ResponseCookie.from("JSESSIONID", "").path(path).httpOnly(true)
                .sameSite("Lax").secure(secure || request.isSecure()).maxAge(Duration.ZERO);
        if (!domain.isBlank()) cookie.domain(domain);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }
}
