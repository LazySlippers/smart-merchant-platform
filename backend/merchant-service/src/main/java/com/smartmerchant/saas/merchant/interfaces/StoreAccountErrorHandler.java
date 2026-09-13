package com.smartmerchant.saas.merchant.interfaces;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice(assignableTypes = MerchantOrganizationController.class)
public class StoreAccountErrorHandler {
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String,String>> accountServiceError(RestClientResponseException error) {
        int status=error.getStatusCode().value();
        if(status==409)return ResponseEntity.status(409).body(Map.of("message","登录账号已被使用，请更换账号后重试"));
        if(status==400)return ResponseEntity.badRequest().body(Map.of("message","账号、密码或权限配置无效，请检查后重试"));
        return ResponseEntity.status(502).body(Map.of("message","账号服务暂时不可用，请稍后重试"));
    }
}
