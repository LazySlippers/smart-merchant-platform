package com.smartmerchant.saas.member;

import com.smartmerchant.saas.member.application.ConsumerAccountService;
import com.smartmerchant.saas.member.application.MemberBenefitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.cloud.nacos.discovery.enabled=false","saas.consumer.runtime-state.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class ConsumerAccountIntegrationTests {
    @Autowired ConsumerAccountService accounts;
    @Autowired MemberBenefitService members;
    @Autowired JwtDecoder decoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test void registerLoginAndIdentityDoNotExposeOtherWallets() throws Exception {
        var session=accounts.register(41,"13800100001","顾客甲","Consumer!12345");
        var jwt=decoder.decode(session.accessToken());
        assertThat(jwt.getClaimAsString("actor_type")).isEqualTo("CONSUMER");
        assertThat(jwt.getClaimAsStringList("permissions")).isEmpty();
        assertThat(accounts.login(41,"13800100001","Consumer!12345").accessToken()).isNotBlank();
        mvc.perform(get("/api/consumer/v1/wallet").header("Authorization","Bearer "+session.accessToken())).andExpect(status().isOk()).andExpect(jsonPath("mobile").value("13800100001"));
        mvc.perform(get("/api/consumer/v1/members").param("tenantId","42").param("mobile","13800100001").header("Authorization","Bearer "+session.accessToken())).andExpect(status().isForbidden());
        mvc.perform(get("/api/consumer/v1/members").param("tenantId","41").param("mobile","13800100002").header("Authorization","Bearer "+session.accessToken())).andExpect(status().isForbidden());
        mvc.perform(get("/api/consumer/v1/members").param("tenantId","41").param("mobile","13800100001")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/merchant/v1/members").header("Authorization","Bearer "+session.accessToken())).andExpect(status().isForbidden());
    }
    @Test void existingMembersCannotBeClaimedWithJustTheirMobile() {
        var old=members.register(41,"13800100003","历史会员");
        assertThatThrownBy(()->accounts.register(41,old.mobile(),"冒领","Consumer!12345")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_account WHERE tenant_id=41 AND mobile='13800100003'",Long.class)).isZero();
    }
    @Test void repeatedBadPasswordsLockAccountAndBrandsRemainSeparate() {
        accounts.register(41,"13800100004","甲","Consumer!12345");
        accounts.register(42,"13800100004","乙","Different!12345");
        for(int i=0;i<5;i++)assertThatThrownBy(()->accounts.login(41,"13800100004","wrong")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        assertThatThrownBy(()->accounts.login(41,"13800100004","Consumer!12345")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("429");
        assertThat(accounts.login(42,"13800100004","Different!12345").accessToken()).isNotBlank();
    }
}
