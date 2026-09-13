package com.smartmerchant.saas.member.application;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.smartmerchant.saas.security.Ids;
import com.smartmerchant.saas.security.SaasSecurityProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

/** Global credentials authorize membership exchange only, never tenant assets. */
@Service
public class UnifiedConsumerService {
    private final JdbcTemplate jdbc;
    private final ConsumerAccountService legacy;
    private final SaasSecurityProperties properties;
    private final TransactionTemplate transactions;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final String dummy = passwords.encode(UUID.randomUUID().toString());

    public UnifiedConsumerService(JdbcTemplate jdbc, ConsumerAccountService legacy,
            SaasSecurityProperties properties, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.legacy=legacy; this.properties=properties;
        this.transactions=new TransactionTemplate(manager);
    }

    @org.springframework.transaction.annotation.Transactional
    public AccountSession register(String mobile, String name, String password) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(BAD_REQUEST,"密码不能超过 72 字节");
        long id=Ids.next();
        try {
            jdbc.update("INSERT INTO unified_consumer_account(id,mobile,display_name,password_hash) VALUES (?,?,?,?)",
                    id,mobile,name,passwords.encode(password));
        } catch(DuplicateKeyException e) { throw new ResponseStatusException(CONFLICT,"统一账号已存在，请登录"); }
        return issue(id,mobile,name);
    }

    public AccountSession login(String mobile,String password) {
        // Return failures from the transaction so lockout updates are committed before rejecting.
        Object result=transactions.execute(status->{
            var rows=jdbc.queryForList("SELECT * FROM unified_consumer_account WHERE mobile=? FOR UPDATE",mobile);
            if(rows.isEmpty()) {passwords.matches(password,dummy);return new ResponseStatusException(UNAUTHORIZED,"统一账号或密码错误");}
            var row=rows.getFirst();
            var failure=verifyPassword(row,password);
            if(failure!=null)return failure;
            jdbc.update("UPDATE unified_consumer_account SET failed_attempts=0,locked_until=NULL WHERE id=?",row.get("id"));
            return issue(((Number)row.get("id")).longValue(),mobile,(String)row.get("display_name"));
        });
        if(result instanceof ResponseStatusException failure)throw failure;
        return (AccountSession)result;
    }

    private ResponseStatusException verifyPassword(Map<String,Object> row,String password) {
        var locked=(Timestamp)row.get("locked_until");
        if(locked!=null && locked.toInstant().isAfter(Instant.now()))return new ResponseStatusException(TOO_MANY_REQUESTS,"尝试次数过多，请稍后再试");
        if(passwords.matches(password,(String)row.get("password_hash")))return null;
        jdbc.update("UPDATE unified_consumer_account SET failed_attempts=failed_attempts+1 WHERE id=?",row.get("id"));
        jdbc.update("UPDATE unified_consumer_account SET locked_until=? WHERE id=? AND failed_attempts>=5",Timestamp.from(Instant.now().plusSeconds(900)),row.get("id"));
        return new ResponseStatusException(UNAUTHORIZED,"账号或当前密码错误");
    }

    public void changePassword(String currentPassword,String newPassword) {
        long account=requireAccount();
        if(newPassword.getBytes(StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(BAD_REQUEST,"密码不能超过 72 字节");
        ResponseStatusException failure=transactions.execute(status->{
            var row=jdbc.queryForMap("SELECT * FROM unified_consumer_account WHERE id=? FOR UPDATE",account);
            var error=verifyPassword(row,currentPassword);
            if(error!=null)return error;
            if(passwords.matches(newPassword,(String)row.get("password_hash")))return new ResponseStatusException(CONFLICT,"新密码不能与当前密码相同");
            jdbc.update("UPDATE unified_consumer_account SET password_hash=?,failed_attempts=0,locked_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",passwords.encode(newPassword),account);
            jdbc.update("UPDATE unified_consumer_session SET revoked=TRUE WHERE account_id=?",account);
            return null;
        });
        if(failure!=null)throw failure;
    }

    public void logout() {
        long owner=requireAccount();
        var token=(JwtAuthenticationToken)SecurityContextHolder.getContext().getAuthentication();
        jdbc.update("UPDATE unified_consumer_session SET revoked=TRUE WHERE id=? AND account_id=?",token.getToken().getClaimAsString("consumer_session"),owner);
    }

    public long requireAccount() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(!(auth instanceof JwtAuthenticationToken token) || !auth.isAuthenticated())
            throw new ResponseStatusException(UNAUTHORIZED,"请先登录统一消费者账号");
        Number id=token.getToken().getClaim("user_id");
        if(!"CONSUMER_ACCOUNT".equals(token.getToken().getClaimAsString("actor_type")) || id==null)
            throw new ResponseStatusException(FORBIDDEN,"需要统一消费者账号");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM unified_consumer_account WHERE id=?",Long.class,id.longValue())!=1)
            throw new ResponseStatusException(UNAUTHORIZED,"账号已失效");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM unified_consumer_session WHERE id=? AND account_id=? AND revoked=FALSE AND expires_at>? AND last_used_at>?",Long.class,token.getToken().getClaimAsString("consumer_session"),id.longValue(),Timestamp.from(Instant.now()),Timestamp.from(Instant.now().minusSeconds(1800)))!=1)
            throw new ResponseStatusException(UNAUTHORIZED,"长时间未使用或登录已失效，请重新验证密码");
        return id.longValue();
    }

    public List<Membership> memberships() {
        return jdbc.query("SELECT b.tenant_id,b.member_id,m.member_name,m.status FROM consumer_membership b JOIN member m ON m.tenant_id=b.tenant_id AND m.id=b.member_id WHERE b.account_id=? ORDER BY b.created_at,b.tenant_id",
                (rs,n)->new Membership(Long.toString(rs.getLong(1)),Long.toString(rs.getLong(2)),rs.getString(3),rs.getString(4)),requireAccount());
    }

    public TenantSession enter(long tenant,String legacyPassword) { return enter(tenant,legacyPassword,false); }

    public TenantSession enter(long tenant,String legacyPassword,boolean join) {
        long account=requireAccount();
        legacy.activeVersion(tenant);
        var owner=jdbc.queryForMap("SELECT * FROM unified_consumer_account WHERE id=?",account);
        String mobile=(String)owner.get("mobile");
        var existingBinding=jdbc.queryForList("SELECT member_id FROM consumer_membership WHERE account_id=? AND tenant_id=?",account,tenant);
        if(existingBinding.isEmpty()&&!join) {
            var global=((JwtAuthenticationToken)SecurityContextHolder.getContext().getAuthentication()).getToken();
            var session=legacy.issue(tenant,account,0,mobile,legacy.activeVersion(tenant),global.getClaimAsString("consumer_session"),global.getExpiresAt());
            return new TenantSession(session.accessToken(),session.expiresIn(),false);
        }
        // Verify outside the binding transaction so failed password/lockout writes survive rejection.
        boolean verified=false;
        if(legacyPassword!=null && !legacyPassword.isBlank() &&
                jdbc.queryForObject("SELECT COUNT(*) FROM consumer_membership WHERE account_id=? AND tenant_id=?",Long.class,account,tenant)==0) {
            legacy.login(tenant,mobile,legacyPassword);
            verified=true;
        }
        final boolean proof=verified;
        try {
            var session=transactions.execute(status->{
                // Serializes concurrent first visits across service instances. Unique keys are the backstop.
                jdbc.queryForObject("SELECT id FROM unified_consumer_account WHERE id=? FOR UPDATE",Long.class,account);
                var bound=jdbc.queryForList("SELECT member_id FROM consumer_membership WHERE account_id=? AND tenant_id=?",account,tenant);
                long member;
                if(!bound.isEmpty()) member=((Number)bound.getFirst().get("member_id")).longValue();
                else {
                    var existing=jdbc.queryForList("SELECT id FROM member WHERE tenant_id=? AND mobile=?",tenant,mobile);
                    if(!existing.isEmpty()) {
                        if(!proof)throw new ResponseStatusException(CONFLICT,"已有商家会员，请验证原商家密码关联；无登录密码的历史会员请联系商家核实");
                        member=jdbc.queryForObject("SELECT member_id FROM consumer_account WHERE tenant_id=? AND mobile=?",Long.class,tenant,mobile);
                    } else {
                        member=Ids.next();
                        jdbc.update("INSERT INTO member(id,tenant_id,mobile,member_name,level_code,status) VALUES (?,?,?,?,'STANDARD','ACTIVE')",member,tenant,mobile,owner.get("display_name"));
                        jdbc.update("INSERT INTO points_account(member_id,tenant_id,available_points,frozen_points) VALUES (?,?,0,0)",member,tenant);
                        jdbc.update("INSERT INTO stored_value_account(member_id,tenant_id,available_cents,frozen_cents) VALUES (?,?,0,0)",member,tenant);
                    }
                    jdbc.update("INSERT INTO consumer_membership(account_id,tenant_id,member_id) VALUES (?,?,?)",account,tenant,member);
                }
                String state=jdbc.queryForObject("SELECT status FROM member WHERE tenant_id=? AND id=?",String.class,tenant,member);
                if(!"ACTIVE".equals(state))throw new ResponseStatusException(FORBIDDEN,"会员已停用");
                var global=((JwtAuthenticationToken)SecurityContextHolder.getContext().getAuthentication()).getToken();
                return legacy.issue(tenant,account,member,mobile,legacy.activeVersion(tenant),global.getClaimAsString("consumer_session"),global.getExpiresAt());
            });
            return new TenantSession(session.accessToken(),session.expiresIn(),true);
        } catch(DuplicateKeyException e) {throw new ResponseStatusException(CONFLICT,"会员关联发生冲突，请刷新后重试或联系商家核实");}
    }

    private AccountSession issue(long id,String mobile,String name) {
        var now=Instant.now();
        String session=UUID.randomUUID().toString();
        jdbc.update("DELETE FROM unified_consumer_session WHERE account_id=? AND (revoked=TRUE OR expires_at<?)",id,Timestamp.from(now));
        jdbc.update("INSERT INTO unified_consumer_session(id,account_id,last_used_at,expires_at) VALUES (?,?,?,?)",session,id,Timestamp.from(now),Timestamp.from(now.plusSeconds(7200)));
        var claims=JwtClaimsSet.builder().issuer(properties.issuer()).subject("consumer-account:"+id)
                .id(UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(7200))
                .claim("actor_type","CONSUMER_ACCOUNT").claim("user_id",id).claim("platform",false)
                .claim("consumer_session",session)
                .claim("permissions",List.of()).build();
        var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(properties.secret().getBytes(StandardCharsets.UTF_8)));
        return new AccountSession(encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue(),7200,mobile,name);
    }
    public record AccountSession(String accessToken,long expiresIn,String mobile,String displayName) {}
    public record TenantSession(String accessToken,long expiresIn,boolean member) {}
    public record Membership(String tenantId,String memberId,String memberName,String status) {}
}
