package com.acme.salarymanagement.filter;

import com.acme.salarymanagement.util.ApiResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

/** Created inside the API security chain, not registered as a servlet-wide filter. */
public class ApiRequestFilter extends OncePerRequestFilter {
    private final ApiResponseWriter responses;

    public ApiRequestFilter(ApiResponseWriter responses) {
        this.responses = responses;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = responses.prepare(request, response);
        try (var ignored = MDC.putCloseable("requestId", id)) {
            chain.doFilter(request, response);
        } catch (ServletException | RuntimeException exception) {
            if (response.isCommitted()) {
                throw exception;
            }
            responses.unexpected(request, response, exception);
        }
    }
}
