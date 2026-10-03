package com.company.autoremediate.security;

import com.company.autoremediate.config.RemediationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Optional shared-secret authentication for /api/** (active when remediation.api.key is set). */
@Component
@ConditionalOnExpression("!'${remediation.api.key:}'.isEmpty()")
public class ApiKeyFilter extends OncePerRequestFilter {
    private final byte[] expected;

    public ApiKeyFilter(RemediationProperties props) {
        this.expected = props.api().key().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader("X-API-Key");
        if (provided == null || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
