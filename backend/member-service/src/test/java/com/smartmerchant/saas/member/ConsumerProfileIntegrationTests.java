package com.smartmerchant.saas.member;

import com.smartmerchant.saas.member.application.ConsumerAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.cloud.nacos.discovery.enabled=false","saas.consumer.runtime-state.enabled=false"})
@AutoConfigureMockMvc @Transactional
class ConsumerProfileIntegrationTests {
 @Autowired ConsumerAccountService accounts;
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired com.smartmerchant.saas.member.interfaces.ConsumerFeedbackController inbox;
 String token(long tenant,String mobile){return "Bearer "+accounts.register(tenant,mobile,"顾客","Consumer!12345").accessToken();}
 @Test void addressesAreValidatedPersistedAndIsolatedByMemberAndTenant() throws Exception {
  String a=token(41,"13800200001"), b=token(41,"13800200002"), other=token(42,"13800200001");
  String body="{\"name\":\"小叶\",\"phone\":\"13800200001\",\"address\":\"杭州市湖滨路 100 号\",\"label\":\"家\",\"isDefault\":true}";
  var response=mvc.perform(post("/api/consumer/v1/profile/addresses").header("Authorization",a).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$[0].isDefault").value(true)).andReturn();
  String id=json.readTree(response.getResponse().getContentAsString()).get(0).get("id").asText();
  for(String outsider:new String[]{b,other}) {
   mvc.perform(get("/api/consumer/v1/profile/addresses").header("Authorization",outsider)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
   mvc.perform(post("/api/consumer/v1/profile/addresses/"+id+"/delete").header("Authorization",outsider).contentType("application/json").content("{}")).andExpect(status().isNotFound());
   mvc.perform(post("/api/consumer/v1/profile/addresses").header("Authorization",outsider).contentType("application/json").content(body.replace("{","{\"id\":\""+id+"\","))).andExpect(status().isNotFound());
  }
  mvc.perform(post("/api/consumer/v1/profile/addresses").header("Authorization",a).contentType("application/json").content(body.replace("13800200001","123"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/consumer/v1/profile/addresses").header("Authorization",a).contentType("application/json").content(body.replace("{","{\"id\":\""+id+"\",").replace("100","200"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].address").value("杭州市湖滨路 200 号"));
  mvc.perform(post("/api/consumer/v1/profile/addresses/"+id+"/delete").header("Authorization",a)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
 }
 @Test void preferencesFavoritesAndFeedbackRequireOwnerIdentity() throws Exception {
  String a=token(41,"13800200003"), b=token(41,"13800200004");
  mvc.perform(get("/api/consumer/v1/profile")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/consumer/v1/profile").header("Authorization",a).contentType("application/json").content("{\"name\":\"新昵称\",\"notifications\":false}")).andExpect(status().isOk()).andExpect(jsonPath("name").value("新昵称")).andExpect(jsonPath("notifications").value(false));
  for(int i=0;i<2;i++)mvc.perform(post("/api/consumer/v1/profile/favorites").header("Authorization",a).contentType("application/json").content("{\"skuId\":\"9223372036854775701\",\"saved\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0]").value("9223372036854775701"));
  mvc.perform(get("/api/consumer/v1/profile/favorites").header("Authorization",b)).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(post("/api/consumer/v1/profile/feedback").header("Authorization",a).contentType("application/json").content("{\"content\":\"希望增加新品\"}")).andExpect(status().isOk()).andExpect(jsonPath("$[0].content").value("希望增加新品"));
  mvc.perform(get("/api/consumer/v1/profile/feedback").header("Authorization",b)).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(post("/api/consumer/v1/profile/feedback").header("Authorization",a).contentType("application/json").content("{\"content\":\"  \"}")).andExpect(status().isBadRequest());
 }
 @Test void onlyOwningHeadOfficeCanProcessFeedback() throws Exception {
  String a=token(41,"13800200005");
  var response=mvc.perform(post("/api/consumer/v1/profile/feedback").header("Authorization",a).contentType("application/json").content("{\"content\":\"反馈隔离验收\"}")).andExpect(status().isOk()).andReturn();
  long id=json.readTree(response.getResponse().getContentAsString()).get(0).get("id").asLong();
  var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
  context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("owner","",org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("merchant:member:view","merchant:member:manage")));
  try {
   com.smartmerchant.saas.security.TenantContextHolder.set(new com.smartmerchant.saas.security.TenantContext(42,701,false,"TENANT_ALL",java.util.Set.of()));
   org.assertj.core.api.Assertions.assertThat((java.util.List<?>)inbox.list(0)).isEmpty();
   org.assertj.core.api.Assertions.assertThatThrownBy(()->inbox.resolve(id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("404");
   com.smartmerchant.saas.security.TenantContextHolder.set(new com.smartmerchant.saas.security.TenantContext(41,701,false,"STORE_SELF",java.util.Set.of()));
   org.assertj.core.api.Assertions.assertThatThrownBy(()->inbox.list(0)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("403");
   com.smartmerchant.saas.security.TenantContextHolder.set(new com.smartmerchant.saas.security.TenantContext(41,701,false,"TENANT_ALL",java.util.Set.of()));
   org.assertj.core.api.Assertions.assertThat((java.util.List<?>)inbox.list(0)).hasSize(1);
   org.assertj.core.api.Assertions.assertThat(((java.util.Map<?,?>)inbox.resolve(id)).get("status")).isEqualTo("RESOLVED");
  } finally {com.smartmerchant.saas.security.TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 }
}
