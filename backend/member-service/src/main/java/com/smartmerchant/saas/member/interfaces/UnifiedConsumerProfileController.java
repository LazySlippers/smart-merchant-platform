package com.smartmerchant.saas.member.interfaces;

import com.smartmerchant.saas.member.application.UnifiedConsumerService;
import com.smartmerchant.saas.security.Ids;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

/** Platform-owned consumer data. Every owner key comes from the verified global token. */
@RestController
@RequestMapping("/api/consumer/v1/auth/account")
public class UnifiedConsumerProfileController {
    private final JdbcTemplate jdbc;
    private final UnifiedConsumerService accounts;
    public UnifiedConsumerProfileController(JdbcTemplate jdbc, UnifiedConsumerService accounts) {this.jdbc=jdbc;this.accounts=accounts;}
    private long owner() {return accounts.requireAccount();}
    private void lock(long owner) {jdbc.queryForObject("SELECT id FROM unified_consumer_account WHERE id=? FOR UPDATE",Long.class,owner);}

    @GetMapping("/privacy") public Object privacy() {
        return Map.of("historyEnabled",jdbc.queryForObject("SELECT history_enabled FROM unified_consumer_account WHERE id=?",Boolean.class,owner()));
    }
    @PostMapping("/privacy") @Transactional public Object privacy(@RequestBody Privacy request) {
        long owner=owner();lock(owner);
        jdbc.update("UPDATE unified_consumer_account SET history_enabled=? WHERE id=?",request.historyEnabled(),owner);
        return Map.of("historyEnabled",request.historyEnabled());
    }

    @GetMapping("/me") public Object me() {
        return jdbc.queryForObject("SELECT mobile,display_name,created_at,updated_at FROM unified_consumer_account WHERE id=?",
                (r,n)->Map.of("mobile",r.getString(1),"displayName",r.getString(2),"createdAt",r.getTimestamp(3).toLocalDateTime().toString(),"updatedAt",r.getTimestamp(4).toLocalDateTime().toString()),owner());
    }
    @PostMapping("/me") @Transactional public Object update(@Valid @RequestBody Profile request) {
        long owner=owner();lock(owner);
        jdbc.update("UPDATE unified_consumer_account SET display_name=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",request.displayName().trim(),owner);
        return me();
    }
    @PostMapping("/password") public Object password(@Valid @RequestBody PasswordChange request) {
        accounts.changePassword(request.currentPassword(),request.newPassword());
        return Map.of("changed",true);
    }

    @GetMapping("/addresses") public List<AddressView> addresses() {
        return jdbc.query("SELECT * FROM unified_consumer_address WHERE account_id=? ORDER BY is_default DESC,updated_at DESC,id DESC",
                (r,n)->new AddressView(Long.toString(r.getLong("id")),r.getString("recipient"),r.getString("phone"),r.getString("province"),r.getString("city"),r.getString("district"),r.getString("detail_address"),r.getString("label"),r.getBoolean("is_default")),owner());
    }
    @PostMapping("/addresses") @Transactional public List<AddressView> saveAddress(@Valid @RequestBody Address request) {
        long owner=owner();lock(owner);
        if(request.id()!=null && jdbc.queryForObject("SELECT COUNT(*) FROM unified_consumer_address WHERE id=? AND account_id=?",Long.class,request.id(),owner)==0)throw new ResponseStatusException(NOT_FOUND,"地址不存在");
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM unified_consumer_address WHERE account_id=?",Long.class,owner);
        if(request.id()==null&&count>=20)throw new ResponseStatusException(CONFLICT,"最多保存 20 个地址");
        boolean makeDefault=request.isDefault()||count==0 || (request.id()!=null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_default FROM unified_consumer_address WHERE id=? AND account_id=?",Boolean.class,request.id(),owner)));
        if(makeDefault)jdbc.update("UPDATE unified_consumer_address SET is_default=FALSE WHERE account_id=?",owner);
        if(request.id()==null)jdbc.update("INSERT INTO unified_consumer_address(id,account_id,recipient,phone,province,city,district,detail_address,label,is_default) VALUES (?,?,?,?,?,?,?,?,?,?)",
                Ids.next(),owner,request.name().trim(),request.phone(),request.province().trim(),request.city().trim(),request.district().trim(),request.detail().trim(),request.label(),makeDefault);
        else jdbc.update("UPDATE unified_consumer_address SET recipient=?,phone=?,province=?,city=?,district=?,detail_address=?,label=?,is_default=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND account_id=?",
                request.name().trim(),request.phone(),request.province().trim(),request.city().trim(),request.district().trim(),request.detail().trim(),request.label(),makeDefault,request.id(),owner);
        return addresses();
    }
    @PostMapping("/addresses/{id}/delete") @Transactional public List<AddressView> deleteAddress(@PathVariable long id) {
        long owner=owner();lock(owner);
        var rows=jdbc.queryForList("SELECT is_default FROM unified_consumer_address WHERE id=? AND account_id=?",Boolean.class,id,owner);
        if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"地址不存在");
        boolean wasDefault=Boolean.TRUE.equals(rows.getFirst());
        if(jdbc.update("DELETE FROM unified_consumer_address WHERE id=? AND account_id=?",id,owner)==0)throw new ResponseStatusException(NOT_FOUND,"地址不存在");
        if(wasDefault) {
            var next=jdbc.queryForList("SELECT id FROM unified_consumer_address WHERE account_id=? ORDER BY updated_at DESC,id DESC LIMIT 1",Long.class,owner);
            if(!next.isEmpty())jdbc.update("UPDATE unified_consumer_address SET is_default=TRUE WHERE id=? AND account_id=?",next.getFirst(),owner);
        }
        return addresses();
    }

    @PostMapping("/history") @Transactional public Object record(@Valid @RequestBody Browse request) {
        long owner=owner();lock(owner);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT history_enabled FROM unified_consumer_account WHERE id=?",Boolean.class,owner)))return Map.of("recorded",false);
        jdbc.update("DELETE FROM consumer_browse_history WHERE account_id=? AND tenant_id=? AND store_id=? AND sku_id=?",owner,request.tenantId(),request.storeId(),request.skuId());
        jdbc.update("INSERT INTO consumer_browse_history(id,account_id,tenant_id,store_id,sku_id,merchant_name,store_name,product_name,sku_name,image_url,price_cents) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                Ids.next(),owner,request.tenantId(),request.storeId(),request.skuId(),request.merchantName().trim(),request.storeName().trim(),request.productName().trim(),request.skuName().trim(),request.imageUrl()==null?"":request.imageUrl().trim(),request.priceCents());
        jdbc.update("DELETE FROM consumer_browse_history WHERE account_id=? AND id NOT IN (SELECT id FROM (SELECT id FROM consumer_browse_history WHERE account_id=? ORDER BY viewed_at DESC,id DESC LIMIT 500) recent)",owner,owner);
        return Map.of("recorded",true);
    }
    @GetMapping("/history") public List<BrowseView> history(@RequestParam(defaultValue="") @Size(max=80) String search,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="0") int days) {
        if(page<0||page>10000||search.length()>80||!Set.of(0,7,30,90).contains(days))throw new ResponseStatusException(BAD_REQUEST,"浏览记录筛选参数无效");
        String term="%"+search.trim()+"%";
        return jdbc.query("SELECT * FROM consumer_browse_history WHERE account_id=? AND viewed_at>=? AND (merchant_name LIKE ? OR store_name LIKE ? OR product_name LIKE ? OR sku_name LIKE ?) ORDER BY viewed_at DESC,id DESC LIMIT 30 OFFSET ?",
                (r,n)->new BrowseView(Long.toString(r.getLong("tenant_id")),Long.toString(r.getLong("store_id")),Long.toString(r.getLong("sku_id")),r.getString("merchant_name"),r.getString("store_name"),r.getString("product_name"),r.getString("sku_name"),r.getString("image_url"),r.getLong("price_cents"),r.getTimestamp("viewed_at").toLocalDateTime()),owner(),java.sql.Timestamp.valueOf(days==0?LocalDateTime.of(1970,1,1,0,0):LocalDateTime.now().minusDays(days)),term,term,term,term,page*30);
    }
    @PostMapping("/history/clear") @Transactional public Object clearHistory() {jdbc.update("DELETE FROM consumer_browse_history WHERE account_id=?",owner());return Map.of("cleared",true);}

    public record Profile(@NotBlank @Size(max=40) String displayName) {}
    public record Privacy(boolean historyEnabled) {}
    public record PasswordChange(@NotNull @Size(min=1,max=64) String currentPassword,@NotNull @Size(min=10,max=64) String newPassword) {}
    public record Address(@Positive Long id,@NotBlank @Size(max=40) String name,@NotNull @Pattern(regexp="1[3-9][0-9]{9}") String phone,@NotBlank @Size(max=64) String province,@NotBlank @Size(max=64) String city,@NotBlank @Size(max=64) String district,@NotBlank @Size(min=3,max=240) String detail,@NotBlank @Size(max=20) String label,boolean isDefault) {}
    public record AddressView(String id,String name,String phone,String province,String city,String district,String detail,String label,boolean isDefault) {}
    public record Browse(@Positive long tenantId,@Positive long storeId,@Positive long skuId,@NotBlank @Size(max=128) String merchantName,@NotBlank @Size(max=128) String storeName,@NotBlank @Size(max=128) String productName,@NotBlank @Size(max=128) String skuName,@Size(max=500) String imageUrl,@PositiveOrZero long priceCents) {}
    public record BrowseView(String tenantId,String storeId,String skuId,String merchantName,String storeName,String productName,String skuName,String imageUrl,long priceCents,LocalDateTime viewedAt) {}
}
