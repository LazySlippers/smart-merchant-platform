package com.smartmerchant.saas.iam.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmerchant.saas.iam.config.IamJwtProperties;
import com.smartmerchant.saas.iam.domain.*;
import com.smartmerchant.saas.iam.infrastructure.*;
import com.smartmerchant.saas.security.Ids;
import com.smartmerchant.saas.security.JwtClaimNames;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.time.Instant; import java.time.LocalDateTime; import java.util.HexFormat; import java.util.List; import java.util.UUID;

@Service public class AuthService {
 private final UserAccountMapper users; private final TenantUserMapper memberships; private final PermissionMapper permissions; private final RefreshTokenMapper refreshTokens; private final LoginAuditService audits; private final PasswordEncoder passwords; private final JwtEncoder encoder; private final IamJwtProperties properties; private final TenantRuntimeStateReader tenantState;
 public AuthService(UserAccountMapper u,TenantUserMapper m,PermissionMapper p,RefreshTokenMapper r,LoginAuditService a,PasswordEncoder pe,JwtEncoder e,IamJwtProperties props,TenantRuntimeStateReader tenantState){users=u;memberships=m;permissions=p;refreshTokens=r;audits=a;passwords=pe;encoder=e;properties=props;this.tenantState=tenantState;}
 @Transactional public TokenPair login(String username,String password,String ip){ var user=users.selectOne(Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getUsername,username)); if(user==null||!"ACTIVE".equals(user.getStatus())||!passwords.matches(password,user.getPasswordHash())){audits.record(0,user==null?null:user.getId(),username,"FAILED","INVALID_CREDENTIALS",ip);throw new IllegalArgumentException("账号或密码错误");} var candidates=memberships.selectList(Wrappers.<TenantUser>lambdaQuery().eq(TenantUser::getUserId,user.getId()).eq(TenantUser::getStatus,"ACTIVE"));var member=candidates.size()==1?candidates.getFirst():null;if(member==null){audits.record(0,user.getId(),username,"FAILED","TENANT_MEMBERSHIP_NOT_FOUND",ip);throw new IllegalArgumentException("账号或密码错误");} var pair=issue(user,member); audits.record(member.getTenantId(),user.getId(),username,"SUCCESS",null,ip); return pair; }
 @Transactional public TokenPair refresh(String raw){ var hash=sha256(raw); var stored=refreshTokens.selectOne(Wrappers.<RefreshToken>lambdaQuery().eq(RefreshToken::getTokenHash,hash).isNull(RefreshToken::getRevokedAt).gt(RefreshToken::getExpiresAt,LocalDateTime.now())); if(stored==null)throw new IllegalArgumentException("Invalid refresh token"); stored.setRevokedAt(LocalDateTime.now());refreshTokens.updateById(stored); var user=users.selectById(stored.getUserId());var member=memberships.selectOne(Wrappers.<TenantUser>lambdaQuery().eq(TenantUser::getTenantId,stored.getTenantId()).eq(TenantUser::getUserId,stored.getUserId()).eq(TenantUser::getStatus,"ACTIVE"));if(user==null||member==null||!"ACTIVE".equals(user.getStatus()))throw new IllegalArgumentException("Account disabled");return issue(user,member);}
 @Transactional public void logout(String raw){refreshTokens.update(null,Wrappers.<RefreshToken>lambdaUpdate().eq(RefreshToken::getTokenHash,sha256(raw)).isNull(RefreshToken::getRevokedAt).set(RefreshToken::getRevokedAt,LocalDateTime.now()));}
 private TokenPair issue(UserAccount user,TenantUser member){ var now=Instant.now();long tenantVersion=tenantState.requireActive(member.getTenantId()); List<String> codes=permissions.findCodes(member.getTenantId(),user.getId()); var claims=JwtClaimsSet.builder().issuer(properties.issuer()).subject(Long.toString(user.getId())).issuedAt(now).expiresAt(now.plus(properties.accessTtl())).id(UUID.randomUUID().toString()).claim(JwtClaimNames.USER_ID,user.getId()).claim(JwtClaimNames.TENANT_ID,member.getTenantId()).claim(JwtClaimNames.TENANT_VERSION,tenantVersion).claim(JwtClaimNames.PLATFORM,member.getTenantId()==0).claim(JwtClaimNames.DATA_SCOPE,member.getDataScope()).claim(JwtClaimNames.PERMISSIONS,codes).claim(JwtClaimNames.TOKEN_TYPE,"access").build(); var header=JwsHeader.with(MacAlgorithm.HS256).build(); var access=encoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue(); var rawRefresh=UUID.randomUUID()+"."+UUID.randomUUID(); var entity=new RefreshToken();entity.setId(Ids.next());entity.setTenantId(member.getTenantId());entity.setUserId(user.getId());entity.setTokenHash(sha256(rawRefresh));entity.setExpiresAt(LocalDateTime.now().plus(properties.refreshTtl()));entity.setCreatedAt(LocalDateTime.now());refreshTokens.insert(entity);return new TokenPair(access,rawRefresh,properties.accessTtl().toSeconds(),user.getDisplayName(),codes);}
 private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public record TokenPair(String accessToken,String refreshToken,long expiresIn,String displayName,List<String> permissions){}
}
