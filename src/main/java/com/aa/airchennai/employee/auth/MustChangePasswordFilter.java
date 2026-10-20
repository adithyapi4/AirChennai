package com.aa.airchennai.employee.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class MustChangePasswordFilter extends OncePerRequestFilter
{
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException
    {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof EmployeeUserDetails employee
                && employee.isMustChangePassword())
        {
            String contextPath = request.getContextPath();
            String path = request.getRequestURI().substring(contextPath.length());

            boolean allowed =
                    path.equals("/employee/change-password")
                            || path.equals("/employee/logout")
                            || path.equals("/error");

            if (!allowed)
            {
                response.sendRedirect(contextPath + "/employee/change-password");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}