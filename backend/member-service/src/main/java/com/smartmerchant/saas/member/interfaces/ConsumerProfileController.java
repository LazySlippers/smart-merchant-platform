package com.smartmerchant.saas.member.interfaces;

import com.smartmerchant.saas.security.ConsumerIdentity;
import com.smartmerchant.saas.security.Ids;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

/** All owner columns come from the verified identity, never the request body. */
@RestController
@RequestMapping("/api/consumer/v1/profile")
public class ConsumerProfileController {
    private final JdbcTemplate jdbc;
    public ConsumerProfileController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private ConsumerIdentity lockOwner() {
        var c = ConsumerIdentity.require();
        jdbc.queryForObject("SELECT id FROM member WHERE tenant_id=? AND id=? FOR UPDATE", Long.class, c.tenantId(), c.memberId());
        return c;
    }
    @GetMapping public Object profile() {
        var c = ConsumerIdentity.require();
        return jdbc.queryForObject("SELECT m.member_name AS name,COALESCE(p.notifications,TRUE) AS notifications FROM member m LEFT JOIN consumer_preferences p ON p.tenant_id=m.tenant_id AND p.member_id=m.id WHERE m.tenant_id=? AND m.id=?", (r,n)->new Profile(r.getString("name"),r.getBoolean("notifications")), c.tenantId(), c.memberId());
    }
    @PostMapping @Transactional public Object update(@Valid @RequestBody Profile r) {
        var c = lockOwner();
        jdbc.update("UPDATE member SET member_name=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?", r.name().trim(), c.tenantId(), c.memberId());
        jdbc.update("DELETE FROM consumer_preferences WHERE tenant_id=? AND member_id=?", c.tenantId(), c.memberId());
        jdbc.update("INSERT INTO consumer_preferences(tenant_id,member_id,notifications) VALUES (?,?,?)", c.tenantId(), c.memberId(), r.notifications());
        return profile();
    }
    @GetMapping("/addresses") public Object addresses() {
        var c = ConsumerIdentity.require();
        return jdbc.query("SELECT * FROM consumer_address WHERE tenant_id=? AND member_id=? ORDER BY is_default DESC,created_at DESC,id DESC", (r,n) -> new AddressView(Long.toString(r.getLong("id")),r.getString("recipient"),r.getString("phone"),r.getString("address"),r.getString("label"),r.getBoolean("is_default")),c.tenantId(),c.memberId());
    }
    @PostMapping("/addresses") @Transactional public Object saveAddress(@Valid @RequestBody Address r) {
        var c = lockOwner();
        if (r.id()!=null && jdbc.queryForObject("SELECT COUNT(*) FROM consumer_address WHERE id=? AND tenant_id=? AND member_id=?",Long.class,r.id(),c.tenantId(),c.memberId())==0) throw new ResponseStatusException(NOT_FOUND,"地址不存在");
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM consumer_address WHERE tenant_id=? AND member_id=?",Long.class,c.tenantId(),c.memberId());
        if(r.id()==null && count>=20) throw new ResponseStatusException(CONFLICT,"最多保存 20 个地址");
        boolean first=count==0;
        if(r.isDefault()||first) jdbc.update("UPDATE consumer_address SET is_default=FALSE WHERE tenant_id=? AND member_id=?",c.tenantId(),c.memberId());
        if(r.id()==null) jdbc.update("INSERT INTO consumer_address(id,tenant_id,member_id,recipient,phone,address,label,is_default) VALUES (?,?,?,?,?,?,?,?)",Ids.next(),c.tenantId(),c.memberId(),r.name().trim(),r.phone(),r.address().trim(),r.label(),r.isDefault()||first);
        else jdbc.update("UPDATE consumer_address SET recipient=?,phone=?,address=?,label=?,is_default=? WHERE id=? AND tenant_id=? AND member_id=?",r.name().trim(),r.phone(),r.address().trim(),r.label(),r.isDefault(),r.id(),c.tenantId(),c.memberId());
        return addresses();
    }
    @PostMapping("/addresses/{id}/delete") @Transactional public Object deleteAddress(@PathVariable long id) {
        var c=lockOwner();
        if(jdbc.update("DELETE FROM consumer_address WHERE id=? AND tenant_id=? AND member_id=?",id,c.tenantId(),c.memberId())==0) throw new ResponseStatusException(NOT_FOUND,"地址不存在");
        return addresses();
    }
    @GetMapping("/favorites") public List<String> favorites() {
        var c=ConsumerIdentity.require();
        return jdbc.query("SELECT sku_id FROM consumer_favorite WHERE tenant_id=? AND member_id=? ORDER BY created_at DESC",(r,n)->Long.toString(r.getLong(1)),c.tenantId(),c.memberId());
    }
    @PostMapping("/favorites") @Transactional public Object favorite(@Valid @RequestBody Favorite r) {
        var c=lockOwner();
        jdbc.update("DELETE FROM consumer_favorite WHERE tenant_id=? AND member_id=? AND sku_id=?",c.tenantId(),c.memberId(),r.skuId());
        if(r.saved()) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_favorite WHERE tenant_id=? AND member_id=?",Long.class,c.tenantId(),c.memberId())>=200) throw new ResponseStatusException(CONFLICT,"最多收藏 200 个商品");
            jdbc.update("INSERT INTO consumer_favorite(tenant_id,member_id,sku_id) VALUES (?,?,?)",c.tenantId(),c.memberId(),r.skuId());
        }
        return favorites();
    }
    @GetMapping("/feedback") public Object feedback() {
        var c=ConsumerIdentity.require();
        return jdbc.query("SELECT * FROM consumer_feedback WHERE tenant_id=? AND member_id=? ORDER BY created_at DESC LIMIT 50",(r,n)->Map.of("id",Long.toString(r.getLong("id")),"content",r.getString("content"),"status",r.getString("status"),"createdAt",r.getTimestamp("created_at").toLocalDateTime().toString()),c.tenantId(),c.memberId());
    }
    @PostMapping("/feedback") @Transactional public Object sendFeedback(@Valid @RequestBody Feedback r) {
        var c=lockOwner();
        jdbc.update("INSERT INTO consumer_feedback(id,tenant_id,member_id,content) VALUES (?,?,?,?)",Ids.next(),c.tenantId(),c.memberId(),r.content().trim());
        return feedback();
    }
    public record Profile(@NotBlank @Size(max=40) String name, boolean notifications) {}
    public record Address(@Positive Long id,@NotBlank @Size(max=40) String name,@NotNull @Pattern(regexp="1[3-9][0-9]{9}") String phone,@NotBlank @Size(min=5,max=240) String address,@NotBlank @Size(max=20) String label,boolean isDefault) {}
    public record AddressView(String id,String name,String phone,String address,String label,boolean isDefault) {}
    public record Favorite(@Positive long skuId,boolean saved) {}
    public record Feedback(@NotBlank @Size(max=1000) String content) {}
}
