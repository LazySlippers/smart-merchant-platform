package com.smartmerchant.saas.merchant.interfaces;

import com.smartmerchant.saas.merchant.application.MerchantOrganizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/merchant/v1")
public class MerchantOrganizationController {
    private final MerchantOrganizationService service;private final com.smartmerchant.saas.merchant.application.StoreAccountService accounts;
    public MerchantOrganizationController(MerchantOrganizationService service,com.smartmerchant.saas.merchant.application.StoreAccountService accounts){this.service=service;this.accounts=accounts;}
    @GetMapping("/stores") @PreAuthorize("hasAuthority('merchant:store:view')") public Object stores(){return service.stores();}
    @GetMapping("/stores/{id}") @PreAuthorize("hasAuthority('merchant:store:view')") public Object store(@PathVariable long id){return service.store(id);}
    @GetMapping("/stores/quota") @PreAuthorize("hasAuthority('merchant:store:view')") public Object storeQuota(){return service.storeQuotaView();}
    @PostMapping("/stores") @PreAuthorize("hasAuthority('merchant:store:manage')") public Object create(@Valid @RequestBody StoreCreate r){return service.createStore(r.storeCode(),r.storeName(),r.address(),r.businessHours());}
    @PutMapping("/stores/{id}") @PreAuthorize("hasAuthority('merchant:store:manage')") public Object update(@PathVariable long id,@Valid @RequestBody StoreUpdate r){return service.updateStore(id,r.storeName(),r.address(),r.businessHours(),r.status(),r.version());}
    @GetMapping("/stores/accounts") @PreAuthorize("hasAuthority('iam:user:manage')") public Object accounts(){return accounts.accounts();}
    @PostMapping("/stores/{id}/account") @PreAuthorize("hasAuthority('iam:user:manage')") public Object createAccount(@PathVariable long id,@Valid @RequestBody StoreAccountCreate r){return accounts.create(id,r.username(),r.password(),r.displayName(),r.permissions());}
    @PutMapping("/stores/{id}/account/password") @PreAuthorize("hasAuthority('iam:user:manage')") public Object resetAccount(@PathVariable long id,@Valid @RequestBody StorePasswordReset r){return accounts.resetPassword(id,r.password());}
    @GetMapping("/stores/{id}/access-accounts") @PreAuthorize("hasAuthority('iam:user:manage')") public Object accessAccounts(@PathVariable long id){return accounts.accessAccounts(id);}
    @PutMapping("/stores/{id}/access-accounts/{userId}/permissions") @PreAuthorize("hasAuthority('iam:user:manage')") public void updatePermissions(@PathVariable long id,@PathVariable long userId,@Valid @RequestBody StorePermissions r){accounts.updatePermissions(id,userId,r.permissions());}
    public record StorePermissions(@NotNull List<String> permissions){}
    @PutMapping("/stores/{id}/account") @PreAuthorize("hasAuthority('iam:user:manage')") public Object editAccount(@PathVariable long id,@Valid @RequestBody StoreAccountEdit r){return accounts.edit(id,r.username(),r.displayName(),r.password());}
    @PostMapping("/stores/{id}/account/reveal") @PreAuthorize("hasAuthority('iam:user:manage')") public Object revealAccount(@PathVariable long id){return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(accounts.reveal(id));}
    public record StoreAccountEdit(@NotBlank @Size(max=128) String username,@NotBlank @Size(max=128) String displayName,@Size(min=8,max=64) String password){}
    @GetMapping("/employees") @PreAuthorize("hasAuthority('merchant:employee:view')") public Object employees(){return service.employees();}
    @PostMapping("/employees") @PreAuthorize("hasAuthority('merchant:employee:manage')") public Object createEmployee(@Valid @RequestBody EmployeeCreate r){return service.createEmployee(r.userId(),r.employeeNo(),r.employeeName(),r.mobile(),r.primaryStoreId(),r.jobTitle(),safe(r.storeIds()));}
    @PutMapping("/employees/{id}") @PreAuthorize("hasAuthority('merchant:employee:manage')") public Object updateEmployee(@PathVariable long id,@Valid @RequestBody EmployeeUpdate r){return service.updateEmployee(id,r.employeeName(),r.mobile(),r.jobTitle(),r.status(),r.version());}
    @PutMapping("/employees/{id}/stores") @PreAuthorize("hasAuthority('merchant:employee:manage')") public Object assign(@PathVariable long id,@Valid @RequestBody StoreAssignment r){return service.assignStores(id,r.primaryStoreId(),safe(r.storeIds()));}
    private static List<Long> safe(List<Long> ids){return ids==null?List.of():List.copyOf(ids);}
    public record StoreCreate(@NotBlank String storeCode,@NotBlank String storeName,String address,String businessHours){}
    public record StoreUpdate(@NotBlank String storeName,String address,String businessHours,@Pattern(regexp="ACTIVE|INACTIVE") String status,@NotNull @PositiveOrZero Integer version){}
    public record StoreAccountCreate(@NotBlank String username,@NotBlank @Size(min=8) String password,@NotBlank String displayName,List<String> permissions){}
    public record StorePasswordReset(@NotBlank @Size(min=8,max=64) String password){}
    public record EmployeeCreate(@Positive long userId,@NotBlank String employeeNo,@NotBlank String employeeName,String mobile,Long primaryStoreId,String jobTitle,List<Long> storeIds){}
    public record EmployeeUpdate(@NotBlank String employeeName,String mobile,String jobTitle,@Pattern(regexp="ACTIVE|INACTIVE") String status,@NotNull @PositiveOrZero Integer version){}
    public record StoreAssignment(Long primaryStoreId,List<Long> storeIds){}
}

