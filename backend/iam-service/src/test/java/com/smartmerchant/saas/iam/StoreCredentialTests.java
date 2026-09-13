package com.smartmerchant.saas.iam;

import com.smartmerchant.saas.iam.application.IamManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="spring.cloud.nacos.discovery.enabled=false")
class StoreCredentialTests {
 @Autowired IamManagementService service;
 @Autowired JdbcTemplate jdbc;
 @Autowired PasswordEncoder encoder;
 @Test void editPreservesIdentityAndPermissionsAndPasswordCanBeRevealed(){
  var user=service.provisionStoreAccount(93001,"credential-store","initial-password","门店");
  var permissions=service.storeAccessAccounts(93001,java.util.List.of(user.getId())).getFirst().permissions();
  var cipher=jdbc.queryForObject("SELECT encrypted_password FROM store_credential WHERE tenant_id=? AND user_id=?",String.class,93001,user.getId());
  assertThat(cipher).doesNotContain("initial-password");
  assertThat(service.revealStorePassword(93001,user.getId(),1)).containsEntry("password","initial-password");
  var edited=service.editStoreAccount(93001,user.getId(),"credential-renamed","新名称",null,1);
  assertThat(edited.getId()).isEqualTo(user.getId());assertThat(encoder.matches("initial-password",edited.getPasswordHash())).isTrue();
  assertThat(service.storeAccessAccounts(93001,java.util.List.of(user.getId())).getFirst().permissions()).isEqualTo(permissions);
  service.editStoreAccount(93001,user.getId(),"credential-renamed","新名称","changed-password",1);
  assertThat(service.revealStorePassword(93001,user.getId(),1)).containsEntry("password","changed-password");
  service.resetStorePassword(93001,user.getId(),"reset-password");
  assertThat(service.revealStorePassword(93001,user.getId(),1)).containsEntry("password","reset-password");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM store_credential_audit WHERE tenant_id=? AND user_id=? AND action='REVEAL'",Long.class,93001,user.getId())).isEqualTo(3);
  assertThatThrownBy(()->service.revealStorePassword(93002,user.getId(),1)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->service.editStoreAccount(93002,user.getId(),"stolen","错误",null,1)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void legacyAndConflictDoNotExposeOrOverwritePasswords(){
  var user=service.provisionStoreAccount(93003,"legacy-credential","legacy-password","旧门店");
  jdbc.update("DELETE FROM store_credential WHERE tenant_id=? AND user_id=?",93003,user.getId());
  assertThat(service.revealStorePassword(93003,user.getId(),1)).containsEntry("available",false).doesNotContainKey("password");
  service.provisionStoreAccount(93004,"occupied-credential","other-password","其他门店");
  assertThatThrownBy(()->service.editStoreAccount(93003,user.getId(),"occupied-credential","旧门店","changed-password",1)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  service.resetStorePassword(93003,user.getId(),"new-legacy-password");
  assertThat(service.revealStorePassword(93003,user.getId(),1)).containsEntry("password","new-legacy-password");
 }
}
