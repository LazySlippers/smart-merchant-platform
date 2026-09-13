package com.smartmerchant.saas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

public final class TenantContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && authentication.isAuthenticated()) {
            var jwt = token.getToken();
            if ("CONSUMER_ACCOUNT".equals(jwt.getClaimAsString("actor_type")) &&
                    !request.getRequestURI().startsWith("/api/consumer/v1/auth/account/")) {
                response.sendError(403, "Global consumer accounts may only exchange membership identities");
                return;
            }
            if ("CONSUMER".equals(jwt.getClaimAsString("actor_type")) &&
                    (request.getRequestURI().startsWith("/api/merchant/") || request.getRequestURI().startsWith("/api/platform/"))) {
                response.sendError(403, "Consumer accounts cannot access management APIs");
                return;
            }
            Number tenantId = jwt.getClaim(JwtClaimNames.TENANT_ID);
            Number userId = jwt.getClaim(JwtClaimNames.USER_ID);
            Boolean platform = jwt.getClaim(JwtClaimNames.PLATFORM);
            String dataScope = jwt.getClaimAsString(JwtClaimNames.DATA_SCOPE);
            List<String> permissions = jwt.getClaimAsStringList(JwtClaimNames.PERMISSIONS);
            if (tenantId != null && userId != null) {
                TenantContextHolder.set(new TenantContext(
                        tenantId.longValue(), userId.longValue(), Boolean.TRUE.equals(platform),
                        dataScope == null ? "OWNER_SELF" : dataScope,
                        permissions == null ? new HashSet<>() : new HashSet<>(permissions)));
            }
        }
        try { chain.doFilter(request, response); }
        finally { TenantContextHolder.clear(); }
    }
}
