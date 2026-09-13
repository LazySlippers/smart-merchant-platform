package com.smartmerchant.saas.merchant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.cloud.nacos.discovery.enabled=false")
class StorefrontHttpErrorTests {
    @Autowired TestRestTemplate http;
    @Test void businessErrorsKeepTheirStatusWithoutOpeningProtectedEndpoints(){
        assertThat(http.getForEntity("/api/consumer/v1/catalog/stores?latitude=91&longitude=120",String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/api/merchant/v1/stores",String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/error",String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-Internal-Key","wrong");
        var body=new HttpEntity<>("{\"tenantId\":1,\"storeId\":1,\"method\":\"PICKUP\",\"items\":[]}",headers);
        assertThat(http.postForEntity("/internal/v1/trade/shipping/quote",body,String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
