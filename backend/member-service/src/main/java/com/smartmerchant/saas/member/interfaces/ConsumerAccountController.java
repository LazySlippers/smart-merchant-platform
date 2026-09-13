package com.smartmerchant.saas.member.interfaces;

import com.smartmerchant.saas.member.application.ConsumerAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/consumer/v1/auth")
public class ConsumerAccountController {
    private final ConsumerAccountService accounts;
    public ConsumerAccountController(ConsumerAccountService accounts) {this.accounts=accounts;}
    @PostMapping("/register") public Object register(@Valid @RequestBody Registration r) {return accounts.register(r.tenantId(),r.mobile(),r.memberName(),r.password());}
    @PostMapping("/login") public Object login(@Valid @RequestBody Login r) {return accounts.login(r.tenantId(),r.mobile(),r.password());}
    public record Registration(@Positive long tenantId,@Pattern(regexp="1[3-9][0-9]{9}") @NotNull String mobile,@NotBlank @Size(max=40) String memberName,@NotNull @Size(min=10,max=64) String password) {}
    public record Login(@Positive long tenantId,@Pattern(regexp="1[3-9][0-9]{9}") @NotNull String mobile,@NotNull @Size(min=1,max=64) String password) {}
}
