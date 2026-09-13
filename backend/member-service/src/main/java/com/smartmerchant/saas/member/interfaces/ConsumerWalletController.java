package com.smartmerchant.saas.member.interfaces;

import com.smartmerchant.saas.member.application.MemberBenefitService;
import com.smartmerchant.saas.security.ConsumerIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/consumer/v1/wallet")
public class ConsumerWalletController {
    private final JdbcTemplate jdbc;
    private final MemberBenefitService members;
    public ConsumerWalletController(JdbcTemplate jdbc,MemberBenefitService members){this.jdbc=jdbc;this.members=members;}
    @GetMapping public Object wallet(){var c=ConsumerIdentity.require();return members.get(c.tenantId(),c.mobile());}
    @GetMapping("/ledgers") public List<Ledger> ledgers(@RequestParam(defaultValue="0") int page){
        var c=ConsumerIdentity.require();
        if(page<0 || page>10000)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid page");
        return jdbc.query("SELECT id,'POINTS' AS kind,action,points_delta AS delta,balance_after AS balance,created_at FROM points_ledger WHERE tenant_id=? AND member_id=? UNION ALL SELECT id,'STORED_VALUE' AS kind,action,amount_delta_cents AS delta,balance_after_cents AS balance,created_at FROM stored_value_ledger WHERE tenant_id=? AND member_id=? ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",
                (r,n)->new Ledger(r.getLong("id"),r.getString("kind"),r.getString("action"),r.getLong("delta"),r.getLong("balance"),r.getTimestamp("created_at").toLocalDateTime()),c.tenantId(),c.memberId(),c.tenantId(),c.memberId(),page*20);
    }
    @GetMapping("/coupons") public List<Coupon> coupons(){
        var c=ConsumerIdentity.require();
        return jdbc.query("SELECT mc.id,mc.template_id,t.template_name,t.discount_cents,t.min_spend_cents,CASE WHEN mc.status='AVAILABLE' AND t.valid_to<=CURRENT_TIMESTAMP THEN 'EXPIRED' ELSE mc.status END AS status,t.valid_from,t.valid_to FROM member_coupon mc JOIN coupon_template t ON mc.tenant_id=t.tenant_id AND mc.template_id=t.id WHERE mc.tenant_id=? AND mc.member_id=? ORDER BY mc.id DESC",
                (r,n)->new Coupon(r.getLong("id"),r.getLong("template_id"),r.getString("template_name"),r.getLong("discount_cents"),r.getLong("min_spend_cents"),r.getString("status"),r.getTimestamp("valid_from").toLocalDateTime(),r.getTimestamp("valid_to").toLocalDateTime()),c.tenantId(),c.memberId());
    }
    @GetMapping("/offers") public List<Coupon> offers(){
        var c=ConsumerIdentity.require();
        return jdbc.query("SELECT t.* FROM coupon_template t WHERE t.tenant_id=? AND t.status='ACTIVE' AND t.valid_from<=CURRENT_TIMESTAMP AND t.valid_to>CURRENT_TIMESTAMP AND t.claimed_quantity<t.total_quantity AND NOT EXISTS(SELECT 1 FROM member_coupon mc WHERE mc.tenant_id=t.tenant_id AND mc.template_id=t.id AND mc.member_id=?) ORDER BY t.id DESC",
                (r,n)->new Coupon(r.getLong("id"),r.getLong("id"),r.getString("template_name"),r.getLong("discount_cents"),r.getLong("min_spend_cents"),"CLAIMABLE",r.getTimestamp("valid_from").toLocalDateTime(),r.getTimestamp("valid_to").toLocalDateTime()),c.tenantId(),c.memberId());
    }
    public record Coupon(long id,long templateId,String name,long discountCents,long minSpendCents,String status,LocalDateTime validFrom,LocalDateTime validTo){}
    public record Ledger(long id,String kind,String action,long delta,long balance,LocalDateTime createdAt){}
}
