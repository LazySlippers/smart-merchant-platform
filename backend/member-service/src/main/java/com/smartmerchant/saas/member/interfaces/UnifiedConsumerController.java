package com.smartmerchant.saas.member.interfaces;

import com.smartmerchant.saas.member.application.UnifiedConsumerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/consumer/v1/auth/account")
public class UnifiedConsumerController {
    private final UnifiedConsumerService accounts;
    public UnifiedConsumerController(UnifiedConsumerService accounts) {this.accounts=accounts;}
    @PostMapping("/register") public Object register(@Valid @RequestBody Registration r) {return accounts.register(r.mobile(),r.memberName(),r.password());}
    @PostMapping("/login") public Object login(@Valid @RequestBody Login r) {return accounts.login(r.mobile(),r.password());}
    @GetMapping("/memberships") public Object memberships() {return accounts.memberships();}
    @PostMapping("/activate") public Object activate() {accounts.requireAccount();return java.util.Map.of("verified",true);}
    @PostMapping("/logout") public Object logout() {accounts.logout();return java.util.Map.of("signedOut",true);}
    @PostMapping("/enter") public Object enter(@Valid @RequestBody Entry r) {return accounts.enter(r.tenantId(),r.legacyPassword(),Boolean.TRUE.equals(r.join()));}
    public record Registration(@NotNull @Pattern(regexp="1[3-9][0-9]{9}") String mobile,@NotBlank @Size(max=40) String memberName,@NotNull @Size(min=10,max=64) String password) {}
    public record Login(@NotNull @Pattern(regexp="1[3-9][0-9]{9}") String mobile,@NotNull @Size(min=1,max=64) String password) {}
    public record Entry(@Positive long tenantId,@Size(max=64) String legacyPassword,Boolean join) {}
}
