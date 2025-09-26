package models;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebFilter(
        filterName = "EmployeeAuthFilter",
        urlPatterns = {
                "/_dashboard",
                "/_dashboard/*",
                "/_dashboard.html"
        },
        dispatcherTypes = {DispatcherType.REQUEST, DispatcherType.FORWARD}
)
public class EmployeeAuthFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpSession session = httpRequest.getSession(false);

        String requestURI = httpRequest.getRequestURI();
        String contextPath = httpRequest.getContextPath();

        String loginPageURI = contextPath + "/_dashboard_login.html";
        String loginActionURI = contextPath + "/_dashboard/login-action";
        String dashboardHtmlURI = contextPath + "/_dashboard.html";

        boolean isLoggedIn = (session != null && session.getAttribute("employeeEmail") != null);
        boolean isRequestingLoginPage = requestURI.equals(loginPageURI);
        boolean isRequestingLoginAction = requestURI.equals(loginActionURI);

        if (isLoggedIn) {
            if (isRequestingLoginPage) {
                httpResponse.sendRedirect(dashboardHtmlURI);
            } else {
                chain.doFilter(request, response);
            }
        } else {
            if (isRequestingLoginPage || isRequestingLoginAction) {
                chain.doFilter(request, response);
            } else {
                httpResponse.sendRedirect(loginPageURI);
            }
        }
    }

    @Override
    public void destroy() {
    }
}