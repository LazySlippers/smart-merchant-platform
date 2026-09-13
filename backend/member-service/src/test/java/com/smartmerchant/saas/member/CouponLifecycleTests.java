package com.smartmerchant.saas.member;

import com.smartmerchant.saas.member.application.MemberBenefitService;
import com.smartmerchant.saas.security.TenantContext;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="spring.cloud.nacos.discovery.enabled=false")
@Transactional
class CouponLifecycleTests {
    @Autowired MemberBenefitService benefits;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void context(){TenantContextHolder.set(new TenantContext(51,701,false,"TENANT_ALL",Set.of("merchant:coupon:manage")));}
    @AfterEach void clear(){TenantContextHolder.clear();}
    private MemberBenefitService.CouponTemplate draft(String code){return benefits.createTemplate(code,"测试满减券",200,1000,10,LocalDateTime.now().minusMinutes(1),LocalDateTime.now().plusDays(1));}

    @Test void draftMustBePublishedAndPausingPreservesClaimedBenefits(){
        var member=benefits.register(51,"13800000001","消费者");
        var next=benefits.register(51,"13800000002","下一位消费者");
        var draft=draft("LIFECYCLE");
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThatThrownBy(()->benefits.claim(51,draft.id(),member.mobile(),"draft")).isInstanceOf(ResponseStatusException.class);
        var active=benefits.changeTemplateStatus(draft.id(),"ACTIVE",draft.version());
        var coupon=benefits.claim(51,draft.id(),member.mobile(),"claim");
        var updated=benefits.templates().getFirst();
        var paused=benefits.changeTemplateStatus(active.id(),"PAUSED",updated.version());
        assertThatThrownBy(()->benefits.claim(51,draft.id(),next.mobile(),"paused")).isInstanceOf(ResponseStatusException.class);
        assertThat(benefits.quote(new MemberBenefitService.BenefitRequest(51,9101,member.mobile(),coupon.id(),0,0,2000)).payableAmountCents()).isEqualTo(1800);
        benefits.changeTemplateStatus(paused.id(),"ACTIVE",paused.version());
        assertThat(benefits.claim(51,draft.id(),next.mobile(),"resumed").id()).isPositive();
        assertThat(benefits.claim(51,draft.id(),member.mobile(),"replay").id()).isEqualTo(coupon.id());
        assertThat(benefits.templates().getFirst().claimedQuantity()).isEqualTo(2);
    }
    @Test void editsAreDraftOnlyAndStatusUsesOptimisticVersion(){
        var draft=draft("EDITS");
        var edited=benefits.editTemplate(draft.id(),"修改草稿",300,1000,20,draft.validFrom(),draft.validTo(),draft.version());
        assertThat(edited.discountCents()).isEqualTo(300);
        assertThatThrownBy(()->benefits.changeTemplateStatus(draft.id(),"ACTIVE",draft.version())).isInstanceOf(ResponseStatusException.class);
        var active=benefits.changeTemplateStatus(draft.id(),"ACTIVE",edited.version());
        assertThatThrownBy(()->benefits.editTemplate(active.id(),"改规则",100,1000,20,active.validFrom(),active.validTo(),active.version())).isInstanceOf(ResponseStatusException.class);
    }
    @Test void anotherTenantCannotPublishOrEditDraft(){
        var draft=draft("ISOLATION");
        TenantContextHolder.set(new TenantContext(52,702,false,"TENANT_ALL",Set.of("merchant:coupon:manage")));
        assertThatThrownBy(()->benefits.changeTemplateStatus(draft.id(),"ACTIVE",draft.version())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->benefits.editTemplate(draft.id(),"跨租户",200,1000,10,draft.validFrom(),draft.validTo(),draft.version())).isInstanceOf(ResponseStatusException.class);
    }
    @Test void expiredAndExhaustedCouponsCannotResume(){
        var draft=draft("EXPIRED");
        jdbc.update("UPDATE coupon_template SET valid_to=? WHERE id=?",LocalDateTime.now().minusMinutes(1),draft.id());
        assertThatThrownBy(()->benefits.changeTemplateStatus(draft.id(),"ACTIVE",draft.version())).isInstanceOf(ResponseStatusException.class);
        var sold=draft("SOLD");
        jdbc.update("UPDATE coupon_template SET claimed_quantity=total_quantity WHERE id=?",sold.id());
        assertThatThrownBy(()->benefits.changeTemplateStatus(sold.id(),"ACTIVE",sold.version())).isInstanceOf(ResponseStatusException.class);
    }
}
