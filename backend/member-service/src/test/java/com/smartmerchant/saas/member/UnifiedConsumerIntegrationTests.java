package com.smartmerchant.saas.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmerchant.saas.member.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.cloud.nacos.discovery.enabled=false","saas.consumer.runtime-state.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class UnifiedConsumerIntegrationTests {
    @Autowired UnifiedConsumerService accounts;
    @Autowired ConsumerAccountService legacy;
    @Autowired MemberBenefitService members;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtDecoder decoder;
    @Autowired ObjectMapper json;
    @Autowired com.smartmerchant.saas.security.SaasSecurityProperties properties;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;

    @Test void suspendedTenantsCannotCreateOrRefreshMembershipsButGlobalLoginStillWorks() {
        var global=register("13800610007");
        var redis=org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String,String> values=org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(redis.opsForValue()).thenReturn(values);
        var runtime=new ConsumerAccountService(jdbc,redis,properties,true);
        var service=new UnifiedConsumerService(jdbc,runtime,properties,manager);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(decoder.decode(global),List.of()));
        try {
            org.mockito.Mockito.when(values.get("saas:tenant:status:64")).thenReturn("SUSPENDED:2");
            assertThatThrownBy(()->service.enter(64,null,true)).hasMessageContaining("404");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE tenant_id=64",Long.class)).isZero();
            org.mockito.Mockito.when(values.get("saas:tenant:status:64")).thenReturn("ACTIVE:3");
            assertThat(decoder.decode(service.enter(64,null,true).accessToken()).getClaim("tenant_version").toString()).isEqualTo("3");
            org.mockito.Mockito.when(values.get("saas:tenant:status:64")).thenReturn("SUSPENDED:4");
            assertThatThrownBy(()->service.enter(64,null,true)).hasMessageContaining("404");
            assertThat(accounts.login("13800610007","Unified!12345").accessToken()).isNotBlank();
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }

    @Test void globalPasswordFailuresLockOneUnifiedAccount() {
        register("13800610008");
        for(int i=0;i<5;i++)assertThatThrownBy(()->accounts.login("13800610008","wrong")).hasMessageContaining("401");
        assertThatThrownBy(()->accounts.login("13800610008","Unified!12345")).hasMessageContaining("429");
        register("13800610009");
        assertThat(accounts.login("13800610009","Unified!12345").accessToken()).isNotBlank();
    }

    String register(String mobile) {return accounts.register(mobile,"统一顾客","Unified!12345").accessToken();}

    @Test void browsingHistoryPaginatesAndFiltersByTimeWithoutLeakingAnotherOwner() throws Exception {
        var token=register("13800610204");var other=register("13800610205");
        Number owner=decoder.decode(token).getClaim("user_id");
        for(int i=1;i<=31;i++)jdbc.update("INSERT INTO consumer_browse_history(id,account_id,tenant_id,store_id,sku_id,merchant_name,store_name,product_name,sku_name,image_url,price_cents,viewed_at) VALUES (?,?,61,611,?,'悦享百货','中心店',?,'500g','',1990,?)",com.smartmerchant.saas.security.Ids.next(),owner.longValue(),6100+i,"燕麦片"+i,java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(i==31?864000:0)));
        String base="/api/consumer/v1/auth/account/history";
        mvc.perform(get(base).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(30));
        mvc.perform(get(base).param("page","1").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].productName").value("燕麦片31"));
        mvc.perform(get(base).param("days","7").param("page","1").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(base).param("search","燕麦片31").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(base).param("search","悦享").param("accountId",owner.toString()).header("Authorization","Bearer "+other)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post(base+"/clear").header("Authorization","Bearer "+other)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_browse_history WHERE account_id=?",Long.class,owner.longValue())).isEqualTo(31);
    }

    @Test void inactivityLogoutAndPasswordChangeInvalidateGlobalAndMerchantSessions() throws Exception {
        var first=register("13800610201");var scoped=enter(first,61,null);
        String session=decoder.decode(first).getClaimAsString("consumer_session");
        jdbc.update("UPDATE unified_consumer_session SET last_used_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1801)),session);
        mvc.perform(post("/api/consumer/v1/auth/account/activate").header("Authorization","Bearer "+first).contentType("application/json").content("{\"lastUsedAt\":9999999999999}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/consumer/v1/wallet").header("Authorization","Bearer "+scoped)).andExpect(status().isUnauthorized());
        var relogin=accounts.login("13800610201","Unified!12345").accessToken();
        var another=accounts.login("13800610201","Unified!12345").accessToken();
        var anotherScoped=enter(another,61,null);
        mvc.perform(post("/api/consumer/v1/auth/account/logout").header("Authorization","Bearer "+relogin)).andExpect(status().isOk());
        mvc.perform(get("/api/consumer/v1/auth/account/addresses").header("Authorization","Bearer "+relogin)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/consumer/v1/auth/account/activate").header("Authorization","Bearer "+another)).andExpect(status().isOk());
        mvc.perform(post("/api/consumer/v1/auth/account/password").header("Authorization","Bearer "+another).contentType("application/json").content("{\"currentPassword\":\"Unified!12345\",\"newPassword\":\"Changed!12345\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/consumer/v1/wallet").header("Authorization","Bearer "+anotherScoped)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/consumer/v1/auth/account/memberships").header("Authorization","Bearer "+another)).andExpect(status().isUnauthorized());
    }

    @Test void addressWritesDefaultsAndHistoryPrivacyCannotCrossAccounts() throws Exception {
        var first=register("13800610202");var second=register("13800610203");
        String base="/api/consumer/v1/auth/account";
        String address="{\"name\":\"小邻\",\"phone\":\"13800610202\",\"province\":\"浙江省\",\"city\":\"杭州市\",\"district\":\"西湖区\",\"detail\":\"文一路18号\",\"label\":\"家\",\"isDefault\":false}";
        var saved=mvc.perform(post(base+"/addresses").header("Authorization","Bearer "+first).contentType("application/json").content(address)).andExpect(status().isOk()).andExpect(jsonPath("$[0].isDefault").value(true)).andReturn();
        String id=json.readTree(saved.getResponse().getContentAsString()).get(0).get("id").asText();
        String update=address.replace("{","{\"id\":\""+id+"\",");
        mvc.perform(post(base+"/addresses").header("Authorization","Bearer "+second).contentType("application/json").content(update)).andExpect(status().isNotFound());
        mvc.perform(post(base+"/addresses/"+id+"/delete").header("Authorization","Bearer "+second)).andExpect(status().isNotFound());
        mvc.perform(post(base+"/addresses").header("Authorization","Bearer "+first).contentType("application/json").content(update)).andExpect(status().isOk()).andExpect(jsonPath("$[0].isDefault").value(true));
        mvc.perform(post(base+"/addresses").header("Authorization","Bearer "+first).contentType("application/json").content(address)).andExpect(status().isOk());
        mvc.perform(post(base+"/addresses/"+id+"/delete").header("Authorization","Bearer "+first)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].isDefault").value(true));
        mvc.perform(post(base+"/privacy").header("Authorization","Bearer "+first).contentType("application/json").content("{\"historyEnabled\":false}")).andExpect(status().isOk());
        mvc.perform(get(base+"/privacy").header("Authorization","Bearer "+second)).andExpect(status().isOk()).andExpect(jsonPath("historyEnabled").value(true));
        String history="{\"tenantId\":61,\"storeId\":611,\"skuId\":6111,\"merchantName\":\"悦享\",\"storeName\":\"中心店\",\"productName\":\"燕麦片\",\"skuName\":\"500g\",\"priceCents\":1990}";
        mvc.perform(post(base+"/history").header("Authorization","Bearer "+first).contentType("application/json").content(history)).andExpect(status().isOk()).andExpect(jsonPath("recorded").value(false));
        mvc.perform(get(base+"/history").header("Authorization","Bearer "+first)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(base+"/history").param("days","-1").header("Authorization","Bearer "+second)).andExpect(status().isBadRequest());
        mvc.perform(post("/internal/v1/member/consumer-session/verify").header("X-Internal-Key","wrong").contentType("application/json").content("{\"sessionId\":\"fake\",\"accountId\":1}")).andExpect(status().isForbidden());
    }
    String enter(String token,long tenant,String password) throws Exception {
        var body=new HashMap<String,Object>();body.put("tenantId",tenant);body.put("join",true);if(password!=null)body.put("legacyPassword",password);
        var response=mvc.perform(post("/api/consumer/v1/auth/account/enter").header("Authorization","Bearer "+token)
                .contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test void globalAddressesHistoryAndSecuritySettingsAreAccountIsolated() throws Exception {
        var first=register("13800610101");var second=register("13800610102");
        var address="{\"name\":\"小邻\",\"phone\":\"13800610101\",\"province\":\"浙江省\",\"city\":\"杭州市\",\"district\":\"西湖区\",\"detail\":\"文一路18号\",\"label\":\"家\",\"isDefault\":true}";
        mvc.perform(post("/api/consumer/v1/auth/account/addresses").header("Authorization","Bearer "+first).contentType("application/json").content(address)).andExpect(status().isOk()).andExpect(jsonPath("$[0].city").value("杭州市"));
        mvc.perform(get("/api/consumer/v1/auth/account/addresses").param("accountId","1").header("Authorization","Bearer "+second)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        var history="{\"tenantId\":61,\"storeId\":611,\"skuId\":6111,\"merchantName\":\"悦享百货\",\"storeName\":\"中心店\",\"productName\":\"燕麦片\",\"skuName\":\"500g\",\"imageUrl\":\"\",\"priceCents\":1990}";
        mvc.perform(post("/api/consumer/v1/auth/account/history").header("Authorization","Bearer "+first).contentType("application/json").content(history)).andExpect(status().isOk());
        mvc.perform(post("/api/consumer/v1/auth/account/history").header("Authorization","Bearer "+first).contentType("application/json").content(history.replace("1990","2090"))).andExpect(status().isOk());
        mvc.perform(get("/api/consumer/v1/auth/account/history").param("search","燕麦").header("Authorization","Bearer "+first)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].priceCents").value(2090));
        mvc.perform(get("/api/consumer/v1/auth/account/history").header("Authorization","Bearer "+second)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/consumer/v1/auth/account/me").header("Authorization","Bearer "+first).contentType("application/json").content("{\"displayName\":\"新昵称\"}")).andExpect(status().isOk()).andExpect(jsonPath("displayName").value("新昵称"));
        mvc.perform(post("/api/consumer/v1/auth/account/password").header("Authorization","Bearer "+first).contentType("application/json").content("{\"currentPassword\":\"wrong\",\"newPassword\":\"Changed!12345\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/consumer/v1/auth/account/password").header("Authorization","Bearer "+first).contentType("application/json").content("{\"currentPassword\":\"Unified!12345\",\"newPassword\":\"Changed!12345\"}")).andExpect(status().isOk());
        assertThatThrownBy(()->accounts.login("13800610101","Unified!12345")).hasMessageContaining("401");
        assertThat(accounts.login("13800610101","Changed!12345").displayName()).isEqualTo("新昵称");
    }

    @Test void oneAccountGetsDistinctTenantMembersAndCannotUseGlobalTokenForAssets() throws Exception {
        var global=register("13800610001");
        assertThat(decoder.decode(global).hasClaim("tenant_id")).isFalse();
        var a=enter(global,61,null);var b=enter(global,62,null);
        Number ma=decoder.decode(a).getClaim("member_id"),mb=decoder.decode(b).getClaim("member_id");
        assertThat(ma.longValue()).isNotEqualTo(mb.longValue());
        assertThat(decoder.decode(enter(global,61,null)).getClaim("member_id").toString()).isEqualTo(ma.toString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_membership WHERE tenant_id IN (61,62)",Long.class)).isEqualTo(2);
        assertThat(accounts.login("13800610001","Unified!12345").accessToken()).isNotBlank();
        mvc.perform(get("/api/consumer/v1/auth/account/memberships").header("Authorization","Bearer "+global)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        for(var path:List.of("/api/consumer/v1/wallet","/api/consumer/v1/wallet/coupons","/api/merchant/v1/members","/api/platform/v1/tenants"))
            mvc.perform(get(path).header("Authorization","Bearer "+global)).andExpect(status().isForbidden());
        mvc.perform(post("/api/consumer/v1/auth/account/enter").header("Authorization","Bearer "+a).contentType("application/json").content("{\"tenantId\":62,\"join\":true}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/consumer/v1/auth/account/memberships")).andExpect(status().isUnauthorized());
        var other=register("13800610002");
        mvc.perform(get("/api/consumer/v1/auth/account/memberships").param("accountId",decoder.decode(global).getClaim("user_id").toString()).header("Authorization","Bearer "+other)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void tenantWalletsAndCouponClaimsStayIsolatedEvenWithForgedHeaders() throws Exception {
        var global=register("13800610003");var a=enter(global,61,null);var b=enter(global,62,null);
        Number ma=decoder.decode(a).getClaim("member_id"),mb=decoder.decode(b).getClaim("member_id");
        jdbc.update("UPDATE stored_value_account SET available_cents=9000 WHERE member_id=?",mb.longValue());
        jdbc.update("INSERT INTO coupon_template(id,tenant_id,template_code,template_name,discount_cents,min_spend_cents,total_quantity,claimed_quantity,valid_from,valid_to,status) VALUES (61001,62,'ONLY_B','乙商家专属',100,1000,10,1,CURRENT_TIMESTAMP,DATEADD('DAY',1,CURRENT_TIMESTAMP),'ACTIVE')");
        jdbc.update("INSERT INTO member_coupon(id,tenant_id,template_id,member_id,status,expires_at) VALUES (61002,62,61001,?,'AVAILABLE',DATEADD('DAY',1,CURRENT_TIMESTAMP))",mb.longValue());
        mvc.perform(get("/api/consumer/v1/wallet").param("tenantId","62").header("X-Tenant-Id","62").header("Authorization","Bearer "+a)).andExpect(status().isOk()).andExpect(jsonPath("storedValueCents").value(0));
        mvc.perform(get("/api/consumer/v1/wallet/coupons").header("Authorization","Bearer "+a)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/consumer/v1/wallet/coupons").header("Authorization","Bearer "+b)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/consumer/v1/coupons/61001/claim").header("Authorization","Bearer "+a).contentType("application/json").content("{\"tenantId\":62,\"mobile\":\"13800610003\",\"requestId\":\"forged\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/consumer/v1/coupons/61001/claim").header("Authorization","Bearer "+a).contentType("application/json").content("{\"tenantId\":61,\"mobile\":\"13800610003\",\"requestId\":\"wrong-template\"}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT available_cents FROM stored_value_account WHERE member_id=?",Long.class,mb.longValue())).isEqualTo(9000);
        jdbc.update("UPDATE member SET status='DISABLED' WHERE id=?",ma.longValue());
        mvc.perform(post("/api/consumer/v1/auth/account/enter").header("Authorization","Bearer "+global).contentType("application/json").content("{\"tenantId\":61,\"join\":true}")).andExpect(status().isForbidden());
    }

    @Test void legacyAssetsRequireOriginalPasswordAndLinkOnlyOnce() throws Exception {
        var old=legacy.register(61,"13800610004","原会员","Original!12345");
        Number member=decoder.decode(old.accessToken()).getClaim("member_id");
        jdbc.update("UPDATE stored_value_account SET available_cents=5000 WHERE member_id=?",member.longValue());
        var global=register("13800610004");
        mvc.perform(post("/api/consumer/v1/auth/account/enter").header("Authorization","Bearer "+global).contentType("application/json").content("{\"tenantId\":61,\"join\":true}")).andExpect(status().isConflict());
        mvc.perform(post("/api/consumer/v1/auth/account/enter").header("Authorization","Bearer "+global).contentType("application/json").content("{\"tenantId\":61,\"legacyPassword\":\"wrong\",\"join\":true}")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM consumer_account WHERE tenant_id=61 AND mobile='13800610004'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_membership WHERE tenant_id=61",Long.class)).isZero();
        var scoped=enter(global,61,"Original!12345");
        assertThat(decoder.decode(scoped).getClaim("member_id").toString()).isEqualTo(member.toString());
        enter(global,61,null);
        mvc.perform(get("/api/consumer/v1/wallet").header("Authorization","Bearer "+scoped)).andExpect(status().isOk()).andExpect(jsonPath("storedValueCents").value(5000));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_membership WHERE tenant_id=61",Long.class)).isEqualTo(1);
    }

    @Test void enteringAStoreDoesNotCreateMembershipUntilConsumerChoosesToJoin() throws Exception {
        var global=register("13800610008");
        var response=mvc.perform(post("/api/consumer/v1/auth/account/enter").header("Authorization","Bearer "+global)
                .contentType("application/json").content("{\"tenantId\":61}"))
                .andExpect(status().isOk()).andExpect(jsonPath("member").value(false)).andReturn();
        var scoped=json.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();
        assertThat(decoder.decode(scoped).getClaim("member_id").toString()).isEqualTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_membership WHERE tenant_id=61",Long.class)).isZero();
        enter(global,61,null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_membership WHERE tenant_id=61",Long.class)).isEqualTo(1);
    }

    @Test void unverifiedOfflineMemberCannotBeAdoptedAndDuplicateGlobalRegistrationIsRejected() throws Exception {
        members.register(61,"13800610005","线下会员");var global=register("13800610005");
        assertThatThrownBy(()->register("13800610005")).hasMessageContaining("409");
        mvc.perform(post("/api/consumer/v1/auth/account/enter").header("Authorization","Bearer "+global).contentType("application/json").content("{\"tenantId\":61,\"memberId\":1,\"accountId\":1,\"join\":true}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_membership WHERE tenant_id=61",Long.class)).isZero();
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentFirstVisitsCreateExactlyOneMemberAndOnePairOfWallets() throws Exception {
        var global=register("13800610006");
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(6)) {
            var tasks=new ArrayList<Future<String>>();
            for(int i=0;i<6;i++)tasks.add(pool.submit(()->{gate.await();return enter(global,63,null);}));
            gate.countDown();
            var ids=new HashSet<String>();
            for(var task:tasks)ids.add(decoder.decode(task.get(20,TimeUnit.SECONDS)).getClaim("member_id").toString());
            assertThat(ids).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_membership WHERE tenant_id=63",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM points_account WHERE tenant_id=63",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stored_value_account WHERE tenant_id=63",Long.class)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM consumer_membership WHERE tenant_id=63");
            jdbc.update("DELETE FROM points_account WHERE tenant_id=63");jdbc.update("DELETE FROM stored_value_account WHERE tenant_id=63");
            jdbc.update("DELETE FROM member WHERE tenant_id=63");jdbc.update("DELETE FROM unified_consumer_account WHERE mobile='13800610006'");
        }
    }
}

