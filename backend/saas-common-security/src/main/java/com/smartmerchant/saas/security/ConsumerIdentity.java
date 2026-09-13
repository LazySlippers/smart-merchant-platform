package com.smartmerchant.saas.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Consumer identity comes exclusively from a verified token, never request parameters. */
public record ConsumerIdentity(long tenantId, long accountId, long memberId, String mobile) {
    public static ConsumerIdentity require() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token) || !auth.isAuthenticated())
            throw new ResponseStatusException(UNAUTHORIZED, "请先登录消费者账号");
        var jwt = token.getToken();
        Number tenant = jwt.getClaim("tenant_id"), account = jwt.getClaim("user_id"), member = jwt.getClaim("member_id");
        if(account==null)account=member;
        String mobile = jwt.getClaimAsString("consumer_mobile");
        if (!"CONSUMER".equals(jwt.getClaimAsString("actor_type")) || tenant == null || account == null || member == null || mobile == null)
            throw new ResponseStatusException(FORBIDDEN, "需要消费者身份");
        return new ConsumerIdentity(tenant.longValue(), account.longValue(), member.longValue(), mobile);
    }

    public void check(long tenant, String requestedMobile) {
        if (tenant != tenantId || (requestedMobile != null && !mobile.equals(requestedMobile)))
            throw new ResponseStatusException(FORBIDDEN, "不能访问其他消费者或品牌的数据");
    }
}
