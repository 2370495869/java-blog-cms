package com.blogcms.security;

import com.blogcms.domain.AppUser;
import com.blogcms.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class EnabledAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public EnabledAccountFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String path = requestUri.substring(request.getContextPath().length());
        return !("/manage".equals(path) || path.startsWith("/manage/")
                || "/account".equals(path) || path.startsWith("/account/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CmsUserDetails principal) {
            AppUser account = users.findById(principal.getId()).orElse(null);
            boolean disabled = account == null || !account.isEnabled();
            boolean roleChanged = !disabled && !account.getRole().name().equals(principal.getRoleName());
            if (disabled || roleChanged) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                String reason = disabled ? "disabled" : "roleChanged";
                response.sendRedirect(response.encodeRedirectURL(
                        request.getContextPath() + "/login?" + reason));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}