package com.smartmerchant.saas.member.application;

import com.smartmerchant.saas.security.Ids;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static org.springframework.http.HttpStatus.*;

/** Tenant-scoped membership and benefit ledger. Amounts are cents; points redeem one cent each in this MVP. */
@Service
public class MemberBenefitService {
    private final JdbcTemplate jdbc; private final AnalyticsOutbox analytics;
    public MemberBenefitService(JdbcTemplate jdbc, AnalyticsOutbox analytics) { this.jdbc = jdbc; this.analytics=analytics; }

    @Transactional public MemberView register(long tenantId, String mobile, String name) {
        if (tenantId <= 0 || blank(mobile) || blank(name)) bad("Invalid member");
        var rows = jdbc.queryForList("SELECT * FROM member WHERE tenant_id=? AND mobile=?", tenantId, mobile);
        if (!rows.isEmpty()) return member(rows.getFirst());
        long id=Ids.next(); var now=LocalDateTime.now();
        try {
            jdbc.update("INSERT INTO member(id,tenant_id,mobile,member_name,level_code,status,created_at,updated_at) VALUES (?,?,?,?,'STANDARD','ACTIVE',?,?)",id,tenantId,mobile,name,now,now);
            jdbc.update("INSERT INTO points_account(member_id,tenant_id,available_points,frozen_points) VALUES (?,?,0,0)",id,tenantId);
            jdbc.update("INSERT INTO stored_value_account(member_id,tenant_id,available_cents,frozen_cents) VALUES (?,?,0,0)",id,tenantId);
        } catch (DuplicateKeyException e) { return member(jdbc.queryForList("SELECT * FROM member WHERE tenant_id=? AND mobile=?",tenantId,mobile).getFirst()); }
        var created=get(tenantId,mobile); analytics.event(tenantId,"MEMBER_REGISTERED",created.id(),Map.of("memberId",created.id())); return created;
    }
    public MemberView get(long tenantId,String mobile){var r=jdbc.queryForList("SELECT * FROM member WHERE tenant_id=? AND mobile=?",tenantId,mobile);if(r.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Member not found");return member(r.getFirst());}
    public List<MemberView> members(){var t=TenantContextHolder.require().tenantId();return jdbc.queryForList("SELECT * FROM member WHERE tenant_id=? ORDER BY id DESC",t).stream().map(this::member).toList();}

    @Transactional public MemberView adjustPoints(long memberId,long delta,String requestId){var t=TenantContextHolder.require().tenantId(); return adjustPoints(t,memberId,delta,"MANUAL:"+requestId,"MANUAL");}
    @Transactional public MemberView adjustStoredValue(long memberId,long delta,String requestId){var t=TenantContextHolder.require().tenantId();return adjustStored(t,memberId,delta,"MANUAL:"+requestId,"MANUAL");}
    @Transactional public CouponTemplate createTemplate(String code,String name,long discount,long minSpend,long quantity,LocalDateTime from,LocalDateTime to){
        long t=TenantContextHolder.require().tenantId();if(blank(code)||blank(name)||discount<=0||minSpend<0||quantity<=0||from==null||to==null||!to.isAfter(from)||!to.isAfter(LocalDateTime.now())||(minSpend>0&&discount>minSpend))bad("Invalid coupon template");
        long id=Ids.next();jdbc.update("INSERT INTO coupon_template(id,tenant_id,template_code,template_name,discount_cents,min_spend_cents,total_quantity,claimed_quantity,valid_from,valid_to,status) VALUES (?,?,?,?,?,?,?,0,?,?,'DRAFT')",id,t,code,name,discount,minSpend,quantity,from,to);return template(id,t);
    }
    @Transactional public CouponTemplate changeTemplateStatus(long id,String status,long version){
        long tenant=TenantContextHolder.require().tenantId();
        var rows=jdbc.queryForList("SELECT * FROM coupon_template WHERE tenant_id=? AND id=?",tenant,id);
        if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Coupon template not found");
        var current=template(rows.getFirst());
        if(!Set.of("ACTIVE","PAUSED").contains(status))bad("Invalid status");
        if(current.version()!=version)conflict("Coupon changed; refresh and try again");
        if(status.equals(current.status()))return current;
        if("ACTIVE".equals(status)){
            if(!Set.of("DRAFT","PAUSED").contains(current.status()))conflict("Cannot publish this coupon");
            if(!current.validTo().isAfter(LocalDateTime.now())||current.claimedQuantity()>=current.totalQuantity())conflict("Coupon expired or sold out");
        }else if(!"ACTIVE".equals(current.status()))conflict("Only published coupons can be paused");
        int changed=jdbc.update("UPDATE coupon_template SET status=?,version=version+1 WHERE tenant_id=? AND id=? AND version=?",status,tenant,id,version);
        if(changed!=1)conflict("Coupon changed; refresh and try again");
        return template(id,tenant);
    }
    @Transactional public CouponTemplate editTemplate(long id,String name,long discount,long minSpend,long quantity,LocalDateTime from,LocalDateTime to,long version){
        long tenant=TenantContextHolder.require().tenantId();
        if(blank(name)||discount<=0||minSpend<0||quantity<=0||from==null||to==null||!to.isAfter(from)||!to.isAfter(LocalDateTime.now())||(minSpend>0&&discount>minSpend))bad("Invalid coupon template");
        int changed=jdbc.update("UPDATE coupon_template SET template_name=?,discount_cents=?,min_spend_cents=?,total_quantity=?,valid_from=?,valid_to=?,version=version+1 WHERE tenant_id=? AND id=? AND status='DRAFT' AND version=?",name,discount,minSpend,quantity,from,to,tenant,id,version);
        if(changed!=1)conflict("Only an unchanged draft can be edited");
        return template(id,tenant);
    }
    public List<CouponTemplate> templates(){long t=TenantContextHolder.require().tenantId();return jdbc.queryForList("SELECT * FROM coupon_template WHERE tenant_id=? ORDER BY id DESC",t).stream().map(this::template).toList();}
    @Transactional public MemberCoupon claim(long tenantId,long templateId,String mobile,String requestId){
        var m=get(tenantId,mobile); var existing=jdbc.queryForList("SELECT * FROM member_coupon WHERE tenant_id=? AND member_id=? AND template_id=?",tenantId,m.id(),templateId);if(!existing.isEmpty())return coupon(existing.getFirst());
        var now=LocalDateTime.now();int changed=jdbc.update("UPDATE coupon_template SET claimed_quantity=claimed_quantity+1,version=version+1 WHERE tenant_id=? AND id=? AND status='ACTIVE' AND valid_from<=? AND valid_to>? AND claimed_quantity<total_quantity",tenantId,templateId,now,now);
        if(changed!=1) conflict("Coupon is unavailable");
        try{jdbc.update("INSERT INTO member_coupon(id,tenant_id,member_id,template_id,status,expires_at) SELECT ?,?,?,id,'AVAILABLE',valid_to FROM coupon_template WHERE tenant_id=? AND id=?",Ids.next(),tenantId,m.id(),tenantId,templateId);}catch(DuplicateKeyException e){
            // A concurrent replay can pass the availability update before the unique
            // member/template constraint rejects its insert.  Put that reservation back.
            jdbc.update("UPDATE coupon_template SET claimed_quantity=claimed_quantity-1,version=version+1 WHERE tenant_id=? AND id=? AND claimed_quantity>0",tenantId,templateId);
            return coupon(jdbc.queryForList("SELECT * FROM member_coupon WHERE tenant_id=? AND member_id=? AND template_id=?",tenantId,m.id(),templateId).getFirst());
        }
        var claimed=coupon(jdbc.queryForList("SELECT * FROM member_coupon WHERE tenant_id=? AND member_id=? AND template_id=?",tenantId,m.id(),templateId).getFirst());analytics.event(tenantId,"COUPON_CLAIMED",claimed.id(),Map.of("memberId",m.id()));return claimed;
    }

    /** Recomputed in freeze so a client cannot rely on a stale trial price. */
    public BenefitQuote quote(BenefitRequest r){ return quoteInternal(r,false); }
    @Transactional public BenefitQuote freeze(BenefitRequest r){ return quoteInternal(r,true); }
    @Transactional public void confirm(BenefitRequest r){apply(r,"CONFIRM");}
    @Transactional public void release(BenefitRequest r){apply(r,"RELEASE");}
    @Transactional public void refund(BenefitRequest r){var m=get(r.tenantId(),r.mobile());String key="ORDER:"+r.orderId();long coupon=benefitAmount(r.tenantId(),key,"COUPON"),points=benefitAmount(r.tenantId(),key,"POINTS"),stored=benefitAmount(r.tenantId(),key,"STORED_VALUE");if(r.couponId()!=null)jdbc.update("UPDATE member_coupon SET status='AVAILABLE',frozen_order_id=NULL WHERE tenant_id=? AND id=? AND member_id=? AND status='USED'",r.tenantId(),r.couponId(),m.id());refundBalance("points_account","available_points",r.tenantId(),m.id(),points,key,"POINTS");refundBalance("stored_value_account","available_cents",r.tenantId(),m.id(),stored,key,"STORED_VALUE");ledger(r.tenantId(),m.id(),r.orderId(),"COUPON","REFUND",coupon,key);ledger(r.tenantId(),m.id(),r.orderId(),"POINTS","REFUND",points,key);ledger(r.tenantId(),m.id(),r.orderId(),"STORED_VALUE","REFUND",stored,key);}
    @Scheduled(fixedDelayString="${saas.member.coupon-expiry-scan-delay-ms:60000}") @Transactional public int expireCoupons(){return jdbc.update("UPDATE member_coupon SET status='EXPIRED' WHERE status='AVAILABLE' AND expires_at<=CURRENT_TIMESTAMP");}

    private BenefitQuote quoteInternal(BenefitRequest r,boolean freeze){
        if(r.tenantId()<=0||r.orderId()<=0||blank(r.mobile())||r.orderAmountCents()<0||r.pointsToUse()<0||r.storedValueToUseCents()<0)bad("Invalid benefit request");
        var m=get(r.tenantId(),r.mobile());if(!"ACTIVE".equals(m.status()))conflict("Member is inactive"); long discount=0;
        if(r.couponId()!=null){var rows=jdbc.queryForList("SELECT mc.*,ct.discount_cents,ct.min_spend_cents FROM member_coupon mc JOIN coupon_template ct ON ct.id=mc.template_id AND ct.tenant_id=mc.tenant_id WHERE mc.tenant_id=? AND mc.id=? AND mc.member_id=?",r.tenantId(),r.couponId(),m.id());if(rows.isEmpty())bad("Coupon does not belong to member");var c=rows.getFirst();if(!"AVAILABLE".equals(value(c,"STATUS"))&&!("FROZEN".equals(value(c,"STATUS"))&&Objects.equals(number(c,"FROZEN_ORDER_ID"),r.orderId()))) conflict("Coupon is unavailable");if(time(c,"EXPIRES_AT").isBefore(LocalDateTime.now())||r.orderAmountCents()<number(c,"MIN_SPEND_CENTS"))conflict("Coupon is not applicable");discount=number(c,"DISCOUNT_CENTS");}
        long remaining=Math.max(0,r.orderAmountCents()-discount),points=Math.min(r.pointsToUse(),remaining);remaining-=points;long stored=Math.min(r.storedValueToUseCents(),remaining);remaining-=stored;
        var pa=jdbc.queryForMap("SELECT available_points FROM points_account WHERE tenant_id=? AND member_id=?",r.tenantId(),m.id());var sa=jdbc.queryForMap("SELECT available_cents FROM stored_value_account WHERE tenant_id=? AND member_id=?",r.tenantId(),m.id());if(number(pa,"AVAILABLE_POINTS")<points||number(sa,"AVAILABLE_CENTS")<stored)conflict("Insufficient member benefit balance");
        var q=new BenefitQuote(m.id(),discount,points,stored,remaining,r.couponId());if(!freeze)return q;
        String key="ORDER:"+r.orderId(); if(r.couponId()!=null){int changed=jdbc.update("UPDATE member_coupon SET status='FROZEN',frozen_order_id=? WHERE tenant_id=? AND id=? AND member_id=? AND status='AVAILABLE'",r.orderId(),r.tenantId(),r.couponId(),m.id());if(changed==0&&!existsBenefit(r.tenantId(),key,"COUPON","FREEZE"))conflict("Coupon is unavailable");}
        freezeBalance("points_account","available_points","frozen_points",r.tenantId(),m.id(),points,key,"POINTS_FREEZE");
        freezeBalance("stored_value_account","available_cents","frozen_cents",r.tenantId(),m.id(),stored,key,"STORED_VALUE_FREEZE");
        ledger(r.tenantId(),m.id(),r.orderId(),"COUPON","FREEZE",discount,key);ledger(r.tenantId(),m.id(),r.orderId(),"POINTS","FREEZE",points,key);ledger(r.tenantId(),m.id(),r.orderId(),"STORED_VALUE","FREEZE",stored,key);return q;
    }
    private void apply(BenefitRequest r,String action){var m=get(r.tenantId(),r.mobile());String key="ORDER:"+r.orderId();boolean confirm="CONFIRM".equals(action);var q=new BenefitQuote(m.id(),benefitAmount(r.tenantId(),key,"COUPON"),benefitAmount(r.tenantId(),key,"POINTS"),benefitAmount(r.tenantId(),key,"STORED_VALUE"),0,r.couponId());
        if(q.couponId()!=null){jdbc.update(confirm?"UPDATE member_coupon SET status='USED',used_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND member_id=? AND status='FROZEN' AND frozen_order_id=?":"UPDATE member_coupon SET status='AVAILABLE',frozen_order_id=NULL WHERE tenant_id=? AND id=? AND member_id=? AND status='FROZEN' AND frozen_order_id=?",r.tenantId(),q.couponId(),m.id(),r.orderId());}
        settleBalance("points_account","available_points","frozen_points",r.tenantId(),m.id(),q.pointsUsed(),confirm,key,"POINTS_"+action);
        settleBalance("stored_value_account","available_cents","frozen_cents",r.tenantId(),m.id(),q.storedValueUsedCents(),confirm,key,"STORED_VALUE_"+action);
        ledger(r.tenantId(),m.id(),r.orderId(),"COUPON",action,q.couponDiscountCents(),key);ledger(r.tenantId(),m.id(),r.orderId(),"POINTS",action,q.pointsUsed(),key);ledger(r.tenantId(),m.id(),r.orderId(),"STORED_VALUE",action,q.storedValueUsedCents(),key);if(confirm&&q.couponId()!=null)analytics.event(r.tenantId(),"COUPON_REDEEMED",q.couponId(),Map.of("memberId",m.id()));
    }
    private void freezeBalance(String table,String available,String frozen,long t,long member,long amount,String key,String action){if(amount==0)return;if(existsBenefit(t,key,action.substring(0,action.indexOf('_')),"FREEZE"))return;int n=jdbc.update("UPDATE "+table+" SET "+available+"="+available+"-?,"+frozen+"="+frozen+"+?,version=version+1 WHERE tenant_id=? AND member_id=? AND "+available+">=?",amount,amount,t,member,amount);if(n!=1)conflict("Insufficient member benefit balance");}
    private void settleBalance(String table,String available,String frozen,long t,long member,long amount,boolean confirm,String key,String action){if(amount==0)return;String type=action.substring(0,action.lastIndexOf('_'));if(existsBenefit(t,key,type,action.substring(action.lastIndexOf('_')+1)))return;int n;if(confirm)n=jdbc.update("UPDATE "+table+" SET "+frozen+"="+frozen+"-?,version=version+1 WHERE tenant_id=? AND member_id=? AND "+frozen+">=?",amount,t,member,amount);else n=jdbc.update("UPDATE "+table+" SET "+frozen+"="+frozen+"-?,"+available+"="+available+"+?,version=version+1 WHERE tenant_id=? AND member_id=? AND "+frozen+">=?",amount,amount,t,member,amount);if(n!=1)conflict("Benefit is not frozen");}
    private void refundBalance(String table,String available,long t,long member,long amount,String key,String type){if(amount==0||existsBenefit(t,key,type,"REFUND"))return;jdbc.update("UPDATE "+table+" SET "+available+"="+available+"+?,version=version+1 WHERE tenant_id=? AND member_id=?",amount,t,member);}
    private MemberView adjustPoints(long t,long id,long delta,String key,String action){var m=memberById(t,id);if(existsPoints(t,key,action))return get(t,m.mobile());int n=jdbc.update("UPDATE points_account SET available_points=available_points+?,version=version+1 WHERE tenant_id=? AND member_id=? AND available_points+?>=0",delta,t,id,delta);if(n!=1)conflict("Insufficient points");long balance=jdbc.queryForObject("SELECT available_points FROM points_account WHERE member_id=?",Long.class,id);jdbc.update("INSERT INTO points_ledger(id,tenant_id,member_id,business_key,action,points_delta,balance_after) VALUES (?,?,?,?,?,?,?)",Ids.next(),t,id,key,action,delta,balance);analytics.event(t,"POINTS_CHANGED",id,Map.of("memberId",id,"pointsDelta",delta));return get(t,m.mobile());}
    private MemberView adjustStored(long t,long id,long delta,String key,String action){var m=memberById(t,id);if(existsStored(t,key,action))return get(t,m.mobile());int n=jdbc.update("UPDATE stored_value_account SET available_cents=available_cents+?,version=version+1 WHERE tenant_id=? AND member_id=? AND available_cents+?>=0",delta,t,id,delta);if(n!=1)conflict("Insufficient stored value");long balance=jdbc.queryForObject("SELECT available_cents FROM stored_value_account WHERE member_id=?",Long.class,id);jdbc.update("INSERT INTO stored_value_ledger(id,tenant_id,member_id,business_key,action,amount_delta_cents,balance_after_cents) VALUES (?,?,?,?,?,?,?)",Ids.next(),t,id,key,action,delta,balance);analytics.event(t,"STORED_VALUE_CHANGED",id,Map.of("memberId",id,"storedValueDeltaCents",delta));return get(t,m.mobile());}
    private void ledger(long t,long m,long order,String type,String action,long amount,String key){if(amount==0||existsBenefit(t,key,type,action))return;jdbc.update("INSERT INTO benefit_ledger(id,tenant_id,member_id,order_id,benefit_type,action,amount_cents,business_key) VALUES (?,?,?,?,?,?,?,?)",Ids.next(),t,m,order,type,action,amount,key);}
    private boolean existsBenefit(long t,String key,String type,String action){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM benefit_ledger WHERE tenant_id=? AND business_key=? AND benefit_type=? AND action=?",Boolean.class,t,key,type,action));}
    private long benefitAmount(long t,String key,String type){var rows=jdbc.queryForList("SELECT amount_cents FROM benefit_ledger WHERE tenant_id=? AND business_key=? AND benefit_type=? AND action='FREEZE'",t,key,type);return rows.isEmpty()?0:number(rows.getFirst(),"AMOUNT_CENTS");}
    private boolean existsPoints(long t,String key,String action){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM points_ledger WHERE tenant_id=? AND business_key=? AND action=?",Boolean.class,t,key,action));}private boolean existsStored(long t,String key,String action){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM stored_value_ledger WHERE tenant_id=? AND business_key=? AND action=?",Boolean.class,t,key,action));}
    private MemberView memberById(long t,long id){var r=jdbc.queryForList("SELECT * FROM member WHERE tenant_id=? AND id=?",t,id);if(r.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Member not found");return member(r.getFirst());}
    private MemberView member(Map<String,Object> r){long id=number(r,"ID"),t=number(r,"TENANT_ID");long p=jdbc.queryForObject("SELECT available_points FROM points_account WHERE member_id=?",Long.class,id),s=jdbc.queryForObject("SELECT available_cents FROM stored_value_account WHERE member_id=?",Long.class,id);return new MemberView(id,t,value(r,"MOBILE"),value(r,"MEMBER_NAME"),value(r,"LEVEL_CODE"),value(r,"STATUS"),p,s,time(r,"CREATED_AT"));}
    private CouponTemplate template(long id,long t){return template(jdbc.queryForMap("SELECT * FROM coupon_template WHERE tenant_id=? AND id=?",t,id));}private CouponTemplate template(Map<String,Object>r){return new CouponTemplate(number(r,"ID"),value(r,"TEMPLATE_CODE"),value(r,"TEMPLATE_NAME"),number(r,"DISCOUNT_CENTS"),number(r,"MIN_SPEND_CENTS"),number(r,"TOTAL_QUANTITY"),number(r,"CLAIMED_QUANTITY"),value(r,"STATUS"),time(r,"VALID_FROM"),time(r,"VALID_TO"),number(r,"VERSION"),jdbc.queryForObject("SELECT COUNT(*) FROM member_coupon WHERE tenant_id=? AND template_id=? AND status='USED'",Long.class,number(r,"TENANT_ID"),number(r,"ID")));}private MemberCoupon coupon(Map<String,Object>r){return new MemberCoupon(number(r,"ID"),number(r,"MEMBER_ID"),number(r,"TEMPLATE_ID"),value(r,"STATUS"));}
    private static boolean blank(String s){return s==null||s.isBlank();} private static Object raw(Map<String,Object>r,String k){Object v=r.get(k);return v==null?r.get(k.toLowerCase(Locale.ROOT)):v;}private static String value(Map<String,Object>r,String k){return String.valueOf(raw(r,k));}private static long number(Map<String,Object>r,String k){Object v=raw(r,k);return v==null?0:((Number)v).longValue();}private static LocalDateTime time(Map<String,Object>r,String k){return ((Timestamp)raw(r,k)).toLocalDateTime();}private static void bad(String m){throw new ResponseStatusException(BAD_REQUEST,m);}private static void conflict(String m){throw new ResponseStatusException(CONFLICT,m);}
    public record MemberView(long id,long tenantId,String mobile,String memberName,String levelCode,String status,long availablePoints,long storedValueCents,LocalDateTime createdAt){} public record CouponTemplate(long id,String templateCode,String templateName,long discountCents,long minSpendCents,long totalQuantity,long claimedQuantity,String status,LocalDateTime validFrom,LocalDateTime validTo,long version,long redeemedQuantity){} public record MemberCoupon(long id,long memberId,long templateId,String status){} public record BenefitRequest(long tenantId,long orderId,String mobile,Long couponId,long pointsToUse,long storedValueToUseCents,long orderAmountCents){} public record BenefitQuote(long memberId,long couponDiscountCents,long pointsUsed,long storedValueUsedCents,long payableAmountCents,Long couponId){}
}
