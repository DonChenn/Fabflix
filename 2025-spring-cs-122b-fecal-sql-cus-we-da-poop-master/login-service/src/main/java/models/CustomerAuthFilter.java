package models;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

//@WebFilter(filterName = "CustomerAuthFilter", urlPatterns = "/*")
public class CustomerAuthFilter implements Filter {
    private final ArrayList<String> allowedURIs = new ArrayList<>();

    @Override
    public void init(FilterConfig fConfig) {
        allowedURIs.add("login.html");
        allowedURIs.add("login.js");
        allowedURIs.add("login.css");
        allowedURIs.add("/login");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Check if this URL is allowed to be accessed without logging in
        if (this.isUrlAllowedWithoutLogin(httpRequest.getRequestURI())) {
            // The URL is allowed, so just continue the filter chain
            chain.doFilter(request, response);
            return;
        }

        String jwt = JwtUtil.getCookieValue(httpRequest, "jwtToken");

        // Check if the user is logged in by looking for the "email" attribute in the session.
        if (jwt == null || JwtUtil.validateToken(jwt) == null) {
            // Token is invalid or doesn't exist, so redirect to login
            httpResponse.sendRedirect("login.html");
        } else {
            // The user is logged in, so allow the request to proceed
            chain.doFilter(request, response);
        }
    }

    private boolean isUrlAllowedWithoutLogin(String requestURI) {
        // Check if the requested URI ends with one of the allowed URIs
        return allowedURIs.stream().anyMatch(uri -> requestURI.toLowerCase().endsWith(uri));
    }

    @Override
    public void destroy() {
        // Required for Filter interface
    }
}