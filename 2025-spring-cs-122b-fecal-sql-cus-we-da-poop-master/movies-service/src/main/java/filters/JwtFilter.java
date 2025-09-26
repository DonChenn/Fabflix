package filters;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import models.JwtUtil; // Already available from common-utils

import java.io.IOException;

public class JwtFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 1. Get the token from the "jwtToken" cookie
        String jwt = JwtUtil.getCookieValue(httpRequest, "jwtToken");

        // 2. Validate the token
        if (jwt == null || JwtUtil.validateToken(jwt) == null) {
            // If the token is missing or invalid, send a 401 Unauthorized error
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication is required.");
        } else {
            // If the token is valid, allow the request to proceed to the servlet
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig fConfig) throws ServletException {
        // No initialization needed
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }
}