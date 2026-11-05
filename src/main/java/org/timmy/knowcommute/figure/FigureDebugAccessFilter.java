package org.timmy.knowcommute.figure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(prefix = "readcast.figures.debug", name = "viewer-enabled", havingValue = "true")
public class FigureDebugAccessFilter extends OncePerRequestFilter {

    private final FigureDebugProperties properties;

    public FigureDebugAccessFilter(FigureDebugProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/debug/figures") && !path.startsWith("/debug/figures/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = properties.viewerToken();
        if (token == null || token.isBlank() || matches(token, request.getHeader("Authorization"))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Basic realm=\"ReadCast Figure Debug\", charset=\"UTF-8\"");
        response.setHeader("Cache-Control", "no-store");
    }

    private static boolean matches(String token, String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }
        try {
            String credentials = new String(Base64.getDecoder().decode(authorization.substring(6)),
                    StandardCharsets.UTF_8);
            int separator = credentials.indexOf(':');
            String password = separator < 0 ? "" : credentials.substring(separator + 1);
            return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                    password.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
