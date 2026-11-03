package com.deshko.orderservice.filter;

import com.deshko.orderservice.security.GatewayUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {
    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String USER_EMAIL = "X-User-Email";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader(USER_ID);
        String role = request.getHeader(USER_ROLE);
        String email = request.getHeader(USER_EMAIL);

        if (StringUtils.hasText(id)
                && StringUtils.hasText(role)
                && StringUtils.hasText(email)) {
            try {
                GatewayUser user = new GatewayUser(Long.valueOf(id), email, role);
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (NumberFormatException _) {}
        }
        chain.doFilter(request, response);
    }
}
