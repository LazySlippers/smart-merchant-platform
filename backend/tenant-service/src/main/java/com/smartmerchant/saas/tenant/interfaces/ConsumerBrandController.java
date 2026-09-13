package com.smartmerchant.saas.tenant.interfaces;

import com.smartmerchant.saas.security.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
public class ConsumerBrandController {
    private final JdbcTemplate jdbc;
    public ConsumerBrandController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping("/api/consumer/v1/catalog/tenants/{tenantId}/brand")
    public Brand brand(@PathVariable long tenantId){
        var rows=jdbc.query("SELECT t.tenant_name,s.display_name,s.logo_url,s.theme_color,s.headline,s.contact_phone,s.version FROM tenant t LEFT JOIN consumer_brand_settings s ON s.tenant_id=t.id WHERE t.id=? AND t.status='ACTIVE'",(r,n)->new Brand(tenantId,r.getString("display_name")==null?r.getString("tenant_name"):r.getString("display_name"),r.getString("logo_url")==null?"":r.getString("logo_url"),r.getString("theme_color")==null?"#284f3d":r.getString("theme_color"),r.getString("headline")==null?"把喜欢的好物带进日常。":r.getString("headline"),r.getString("contact_phone")==null?"":r.getString("contact_phone"),r.getInt("version")),tenantId);
        if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"品牌暂不可用");return rows.getFirst();
    }
    private long tenant(){var c=TenantContextHolder.require();if(c.platform()||!"TENANT_ALL".equals(c.dataScope()))throw new ResponseStatusException(FORBIDDEN,"仅品牌总部可配置商城");return c.tenantId();}
    @GetMapping("/api/merchant/v1/consumer-settings") @PreAuthorize("hasAuthority('merchant:store:manage')")
    public Brand settings(){return brand(tenant());}
    @PutMapping("/api/merchant/v1/consumer-settings") @PreAuthorize("hasAuthority('merchant:store:manage')") @Transactional
    public Brand update(@Valid @RequestBody Settings r){
        long tenant=tenant();
        jdbc.queryForList("SELECT id FROM tenant WHERE id=? FOR UPDATE",tenant);
        var current=brand(tenant);if(current.version()!=r.version())throw new ResponseStatusException(CONFLICT,"配置已变化，请刷新后重试");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_brand_settings WHERE tenant_id=?",Long.class,tenant)==0)
            jdbc.update("INSERT INTO consumer_brand_settings(tenant_id,display_name,logo_url,theme_color,headline,contact_phone,version) VALUES (?,?,?,?,?,?,1)",tenant,r.displayName(),r.logoUrl(),r.themeColor(),r.headline(),r.contactPhone());
        else jdbc.update("UPDATE consumer_brand_settings SET display_name=?,logo_url=?,theme_color=?,headline=?,contact_phone=?,version=version+1 WHERE tenant_id=?",r.displayName(),r.logoUrl(),r.themeColor(),r.headline(),r.contactPhone(),tenant);
        jdbc.update("INSERT INTO tenant_audit_log(id,operator_id,action,resource_type,resource_id,detail) VALUES (?,?,'CONSUMER_BRAND_UPDATED','TENANT',?,?)",com.smartmerchant.saas.security.Ids.next(),TenantContextHolder.require().userId(),String.valueOf(tenant),"消费者商城配置已更新，版本 "+(current.version()+1));
        return brand(tenant);
    }
    public record Brand(long tenantId,String displayName,String logoUrl,String themeColor,String headline,String contactPhone,int version){}
    public record Settings(@NotBlank @Size(max=128) String displayName,@NotNull @Size(max=1000) @Pattern(regexp="^$|https?://[^\\s]+") String logoUrl,@NotNull @Pattern(regexp="#[0-9a-fA-F]{6}") String themeColor,@NotBlank @Size(max=80) String headline,@NotNull @Pattern(regexp="[0-9+() -]{0,32}") String contactPhone,@PositiveOrZero int version){}
}
