package com.project.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(Ordered.LOWEST_PRECEDENCE)
public class GatewayErrorResponseFilter extends OncePerRequestFilter {
    public static final int FILTER_ORDER = Ordered.LOWEST_PRECEDENCE;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
            if (!response.isCommitted() && response.getStatus() >= 400 && response.getContentType() == null) {
                writeError(response, response.getStatus());
            }
        } catch (Exception exception) {
            if (!response.isCommitted()) {
                response.resetBuffer();
                writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private static void writeError(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String error = switch (status) {
            case 404 -> "not_found";
            case 429 -> "too_many_requests";
            case 502 -> "bad_gateway";
            default -> status >= 500 ? "internal_server_error" : "gateway_error";
        };
        String message = status >= 500 ? "An unexpected error occurred." : "The gateway could not complete the request.";
        response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
