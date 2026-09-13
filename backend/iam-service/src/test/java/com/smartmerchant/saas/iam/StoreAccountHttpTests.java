package com.smartmerchant.saas.iam;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.cloud.nacos.discovery.enabled=false")
class StoreAccountHttpTests {
    @Autowired TestRestTemplate http;
    @Autowired com.smartmerchant.saas.iam.application.IamManagementService management;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Test void loginUsesOnlyAccountAndPassword() throws Exception {
        management.provisionStoreAccount(88009,"http-password-only","StrongPass!123","门店");
        var response=http.postForEntity("/api/auth/v1/login",Map.of("username","http-password-only","password","StrongPass!123"),String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(response.getBody()).get("accessToken").asText()).isNotBlank();
        var invalid=http.postForEntity("/api/auth/v1/login",Map.of("username","http-password-only","password","wrong"),String.class);
        assertThat(invalid.getStatusCode().is4xxClientError()).isTrue();
    }
    @Test void editingAndRevealUseInternalKeyAndNoStore() throws Exception {
        var created=create(key,"http-editable-store");
        long id=json.readTree(created.getBody()).get("id").asLong();
        var headers=new HttpHeaders();headers.set("X-Internal-Key",key);
        var edited=http.exchange("/internal/v1/store-accounts/"+id,HttpMethod.PUT,new HttpEntity<>(Map.of("tenantId",88001,"operatorId",1,"username","http-renamed-store","displayName","修改名称","password","edited-test-password"),headers),String.class);
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody()).doesNotContain("edited-test-password");
        var reveal=http.postForEntity("/internal/v1/store-accounts/"+id+"/reveal",new HttpEntity<>(Map.of("tenantId",88001,"operatorId",1),headers),String.class);
        assertThat(reveal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reveal.getHeaders().getCacheControl()).contains("no-store");
        assertThat(json.readTree(reveal.getBody()).get("password").asText()).isEqualTo("edited-test-password");
        headers.set("X-Internal-Key","invalid");
        assertThat(http.postForEntity("/internal/v1/store-accounts/"+id+"/reveal",new HttpEntity<>(Map.of("tenantId",88001,"operatorId",1),headers),String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Value("${saas.security.internal-signing-key}") String key;
    private ResponseEntity<String> create(String secret,String username){var headers=new HttpHeaders();headers.set("X-Internal-Key",secret);return http.postForEntity("/internal/v1/store-accounts",new HttpEntity<>(Map.of("tenantId",88001,"username",username,"password","store-test-password","displayName","测试门店"),headers),String.class);}
    @Test void internalAccountCreationWorksWithoutBearerToken(){assertThat(create(key,"http-store-test").getStatusCode()).isEqualTo(HttpStatus.OK);}
    @Test void wrongInternalKeyRemainsForbiddenInsteadOfUnauthorized(){assertThat(create("invalid-key","http-denied").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);}
    @Test void existingOwnerUsernameReturnsConflict(){management.provisionOwner(88001,"http-existing-owner","owner-password","老板",null);assertThat(create(key,"http-existing-owner").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);}
    @Test void validationErrorRemainsBadRequest(){var headers=new HttpHeaders();headers.set("X-Internal-Key",key);assertThat(http.postForEntity("/internal/v1/store-accounts",new HttpEntity<>(Map.of(),headers),String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);}
}
