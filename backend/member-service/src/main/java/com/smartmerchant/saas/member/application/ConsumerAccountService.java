package com.smartmerchant.saas.member.application;

import com.smartmerchant.saas.security.Ids;
import com.smartmerchant.saas.security.SaasSecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.springframework.http.HttpStatus.*;

@Service
public class ConsumerAccountService {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final SaasSecurityProperties properties;
    private final boolean runtimeEnabled;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final String dummyHash = passwords.encode(UUID.randomUUID().toString());
    public ConsumerAccountService(JdbcTemplate jdbc, StringRedisTemplate redis, SaasSecurityProperties properties,
            @Value("${saas.consumer.runtime-state.enabled:true}") boolean runtimeEnabled) {
        this.jdbc=jdbc; this.redis=redis; this.properties=properties; this.runtimeEnabled=runtimeEnabled;
    }

    @Transactional
    public Session register(long tenant, String mobile, String name, String password) {
        if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(BAD_REQUEST,"密码过长，请使用不超过 72 字节的密码");
        long version = activeVersion(tenant);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE tenant_id=? AND mobile=?", Long.class, tenant, mobile) > 0)
            throw new ResponseStatusException(CONFLICT, "该账号已存在，请登录；历史会员需由门店核实后绑定");
        long member = Ids.next(), account = Ids.next();
        // Insert directly: never adopt an existing member or their assets by knowing a phone number.
        jdbc.update("INSERT INTO member(id,tenant_id,mobile,member_name,level_code,status,created_at,updated_at) VALUES (?,?,?,?,'STANDARD','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", member,tenant,mobile,name);
        jdbc.update("INSERT INTO points_account(member_id,tenant_id,available_points,frozen_points) VALUES (?,?,0,0)", member,tenant);
        jdbc.update("INSERT INTO stored_value_account(member_id,tenant_id,available_cents,frozen_cents) VALUES (?,?,0,0)", member,tenant);
        jdbc.update("INSERT INTO consumer_account(id,tenant_id,member_id,mobile,password_hash) VALUES (?,?,?,?,?)", account,tenant,member,mobile,passwords.encode(password));
        return issue(tenant, account, member, mobile, version);
    }

    public Session login(long tenant, String mobile, String password) {
        long version=activeVersion(tenant);
        var rows=jdbc.queryForList("SELECT a.*,m.status AS member_status FROM consumer_account a JOIN member m ON m.tenant_id=a.tenant_id AND m.id=a.member_id WHERE a.tenant_id=? AND a.mobile=?",tenant,mobile);
        if(rows.isEmpty()) { passwords.matches(password,dummyHash); throw new ResponseStatusException(UNAUTHORIZED,"账号或密码错误"); }
        var r=rows.getFirst();
        var lock=(java.sql.Timestamp)r.get("locked_until");
        if(lock!=null && lock.toInstant().isAfter(Instant.now())) throw new ResponseStatusException(TOO_MANY_REQUESTS,"尝试次数过多，请稍后再试");
        if(!passwords.matches(password,(String)r.get("password_hash"))) {
            jdbc.update("UPDATE consumer_account SET failed_attempts=failed_attempts+1 WHERE tenant_id=? AND mobile=?",tenant,mobile);
            jdbc.update("UPDATE consumer_account SET locked_until=? WHERE tenant_id=? AND mobile=? AND failed_attempts>=5",java.sql.Timestamp.from(Instant.now().plusSeconds(900)),tenant,mobile);
            throw new ResponseStatusException(UNAUTHORIZED,"账号或密码错误");
        }
        if(!"ACTIVE".equals(r.get("member_status")))throw new ResponseStatusException(FORBIDDEN,"会员已停用");
        jdbc.update("UPDATE consumer_account SET failed_attempts=0,locked_until=NULL WHERE tenant_id=? AND mobile=?",tenant,mobile);
        return issue(tenant,((Number)r.get("id")).longValue(),((Number)r.get("member_id")).longValue(),mobile,version);
    }

    long activeVersion(long tenant) {
        if(!runtimeEnabled)return 0;
        String state=redis.opsForValue().get("saas:tenant:status:"+tenant);
        if(state==null || !state.startsWith("ACTIVE:"))throw new ResponseStatusException(NOT_FOUND,"品牌暂不可用");
        return Long.parseLong(state.substring(7));
    }
    Session issue(long tenant,long account,long member,String mobile,long version) {
        return issue(tenant,account,member,mobile,version,null,Instant.now().plusSeconds(7200));
    }
    Session issue(long tenant,long account,long member,String mobile,long version,String session,Instant expires) {
        var now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer(properties.issuer()).subject("consumer:"+account).id(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(expires).claim("actor_type","CONSUMER")
                .claim("tenant_id",tenant).claim("tenant_version",version).claim("user_id",account)
                .claim("member_id",member).claim("consumer_mobile",mobile).claim("platform",false)
                .claim("data_scope","CONSUMER_SELF").claim("permissions",List.of())
                .claims(values->{if(session!=null)values.put("consumer_session",session);}).build();
        var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(properties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return new Session(encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue(),Math.max(0,java.time.Duration.between(now,expires).toSeconds()));
    }
    public record Session(String accessToken,long expiresIn) {}
}
