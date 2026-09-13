package com.smartmerchant.saas.iam.interfaces;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class IamErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Object> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        var body=new LinkedHashMap<String,Object>();
        body.put("timestamp", OffsetDateTime.now());body.put("status",exception.getStatusCode().value());
        body.put("error",exception.getStatusCode().toString());body.put("message",exception.getReason()==null?"请求处理失败":exception.getReason());
        body.put("path",request.getRequestURI());return ResponseEntity.status(exception.getStatusCode()).body(body);
    }
}
