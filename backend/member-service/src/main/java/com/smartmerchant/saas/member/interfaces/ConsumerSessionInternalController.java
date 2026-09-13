package com.smartmerchant.saas.member.interfaces;

import com.smartmerchant.saas.security.ConsumerSessionVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
public class ConsumerSessionInternalController {
    private final ConsumerSessionVerifier sessions;
    private final String key;
    public ConsumerSessionInternalController(ConsumerSessionVerifier sessions,@Value("${saas.security.internal-signing-key}") String key) {this.sessions=sessions;this.key=key;}
    @PostMapping("/internal/v1/member/consumer-session/verify")
    public boolean verify(@RequestHeader("X-Internal-Key") String provided,@RequestBody Session request) {
        if(!java.security.MessageDigest.isEqual(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),provided.getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new ResponseStatusException(FORBIDDEN);
        return sessions.verify(request.sessionId(),request.accountId());
    }
    public record Session(String sessionId,long accountId) {}
}
