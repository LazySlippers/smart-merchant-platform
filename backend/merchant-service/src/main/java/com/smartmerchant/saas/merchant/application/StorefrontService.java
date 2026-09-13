package com.smartmerchant.saas.merchant.application;

import com.smartmerchant.saas.security.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class StorefrontService {
    public static final Set<String> PROVINCES = Set.of("北京市","天津市","河北省","山西省","内蒙古自治区","辽宁省","吉林省","黑龙江省","上海市","江苏省","浙江省","安徽省","福建省","江西省","山东省","河南省","湖北省","湖南省","广东省","广西壮族自治区","海南省","重庆市","四川省","贵州省","云南省","西藏自治区","陕西省","甘肃省","青海省","宁夏回族自治区","新疆维吾尔自治区","香港特别行政区","澳门特别行政区","台湾省");
    private final JdbcTemplate jdbc;
    private final StoreAccessGuard access;
    private final ConsumerCatalogService catalog;
    public StorefrontService(JdbcTemplate jdbc, StoreAccessGuard access, ConsumerCatalogService catalog) { this.jdbc=jdbc; this.access=access; this.catalog=catalog; }

    public Settings settings(long storeId) { access.requireAccess(storeId); return read(TenantContextHolder.require().tenantId(),storeId); }
    public Settings publicSettings(long tenantId,long storeId) {
        catalog.requireActiveTenant(tenantId);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM store WHERE tenant_id=? AND id=? AND status='ACTIVE' AND marketplace_listed=TRUE",Long.class,tenantId,storeId)==0)throw new ResponseStatusException(NOT_FOUND,"门店暂未营业");
        return read(tenantId,storeId);
    }
    private Settings read(long tenantId,long storeId) {
        var rows=jdbc.query("SELECT * FROM store WHERE tenant_id=? AND id=?",(r,n)->new Settings(r.getBoolean("marketplace_listed"),(Double)r.getObject("latitude"),(Double)r.getObject("longitude"),r.getString("city"),r.getString("district"),r.getString("cover_url"),r.getBoolean("pickup_enabled"),r.getBoolean("shipping_enabled"),r.getLong("first_shipping_cents"),r.getLong("extra_shipping_cents"),split(r.getString("excluded_provinces")),split(r.getString("pickup_only_skus")),r.getInt("storefront_version")),tenantId,storeId);
        if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"门店不存在");
        return rows.getFirst();
    }
    @Transactional public Settings update(long storeId,Settings s) {
        access.requireAccess(storeId);
        coordinates(s.latitude(),s.longitude());
        if(s.city()==null||s.city().length()>80||s.district()==null||s.district().length()>80||s.coverUrl()==null||s.coverUrl().length()>512||(!s.coverUrl().isBlank()&&!s.coverUrl().startsWith("https://")&&!s.coverUrl().startsWith("/")))bad("请检查地区和封面地址");
        if(!s.pickupEnabled()&&!s.shippingEnabled())bad("至少开启一种履约方式");
        if(s.firstShippingCents()<0||s.extraShippingCents()<0||s.firstShippingCents()>1000000||s.extraShippingCents()>1000000)bad("运费应为 0 至 10000 元");
        if(s.excludedProvinces()==null||!PROVINCES.containsAll(s.excludedProvinces())||s.pickupOnlySkus()==null||s.pickupOnlySkus().size()>300)bad("配送限制无效");
        long tenant=TenantContextHolder.require().tenantId();
        for(String sku:s.pickupOnlySkus())if(!sku.matches("[1-9][0-9]{0,18}")||jdbc.queryForObject("SELECT COUNT(*) FROM store_product WHERE tenant_id=? AND store_id=? AND sku_id=?",Long.class,tenant,storeId,sku)==0)bad("仅自提商品必须属于本店");
        int changed=jdbc.update("""
            UPDATE store SET marketplace_listed=?,latitude=?,longitude=?,city=?,district=?,cover_url=?,pickup_enabled=?,shipping_enabled=?,first_shipping_cents=?,extra_shipping_cents=?,excluded_provinces=?,pickup_only_skus=?,storefront_version=storefront_version+1
            WHERE tenant_id=? AND id=? AND storefront_version=?
            """,s.marketplaceListed(),s.latitude(),s.longitude(),s.city().trim(),s.district().trim(),s.coverUrl(),s.pickupEnabled(),s.shippingEnabled(),s.firstShippingCents(),s.extraShippingCents(),String.join(",",s.excludedProvinces()),String.join(",",s.pickupOnlySkus()),tenant,storeId,s.version());
        if(changed!=1)throw new ResponseStatusException(CONFLICT,"配置已变化，请刷新后重试");
        return read(tenant,storeId);
    }
    // Public discovery returns only storefront information, never membership or operational data.
    public List<NearbyStore> discover(Double latitude,Double longitude,String area,String search,int page) {
        coordinates(latitude,longitude);
        if(page<0||page>10000||area.length()>80||search.length()>100)bad("查询参数无效");
        String region="%"+escape(area.trim())+"%", keyword="%"+escape(search.trim())+"%";
        var rows=jdbc.query("""
            SELECT id,tenant_id,store_name,address,business_hours,city,district,cover_url,latitude,longitude,pickup_enabled,shipping_enabled
            FROM store WHERE status='ACTIVE' AND marketplace_listed=TRUE
            AND (city LIKE ? ESCAPE '!' OR district LIKE ? ESCAPE '!' OR address LIKE ? ESCAPE '!')
            AND store_name LIKE ? ESCAPE '!'
            """,(r,n)->new NearbyStore(r.getLong("id"),r.getLong("tenant_id"),r.getString("store_name"),r.getString("address"),r.getString("business_hours"),r.getString("city"),r.getString("district"),r.getString("cover_url"),r.getBoolean("pickup_enabled"),r.getBoolean("shipping_enabled"),distance(latitude,longitude,(Double)r.getObject("latitude"),(Double)r.getObject("longitude"))),region,region,region,keyword);
        var active=new HashMap<Long,Boolean>();
        return rows.stream().filter(s->active.computeIfAbsent(s.tenantId(),catalog::isActiveTenant))
            .sorted(Comparator.comparing(NearbyStore::distanceKm,Comparator.nullsLast(Double::compareTo)).thenComparingLong(NearbyStore::id)).skip((long)page*20).limit(20).toList();
    }
    public long freight(long tenant,long store,String method,String province,List<ConsumerCatalogService.QuoteRequestItem> items) {
        var s=publicSettings(tenant,store);
        if("PICKUP".equals(method)){if(!s.pickupEnabled())bad("门店不支持自提");return 0;}
        if(!"SHIPPING".equals(method)||!s.shippingEnabled())bad("门店不支持邮寄");
        if(!PROVINCES.contains(province==null?"":province)||s.excludedProvinces().contains(province))bad("所选地区暂不配送");
        if(items==null||items.isEmpty())bad("请选择商品");
        long quantity=0;
        for(var item:items){if(item.quantity()<=0)bad("商品数量无效");if(s.pickupOnlySkus().contains(String.valueOf(item.skuId())))bad("订单含仅自提商品，请分开购买");quantity=Math.addExact(quantity,item.quantity());}
        return Math.addExact(s.firstShippingCents(),Math.multiplyExact(s.extraShippingCents(),quantity-1));
    }
    public static void coordinates(Double lat,Double lon){if((lat==null)!=(lon==null)||lat!=null&&(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180))bad("经纬度无效");}
    private static Double distance(Double a,Double b,Double c,Double d){if(a==null||c==null||d==null)return null;double x=Math.pow(Math.sin(Math.toRadians(c-a)/2),2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.pow(Math.sin(Math.toRadians(d-b)/2),2);return Math.round(6371*2*Math.asin(Math.sqrt(Math.min(1,x)))*1000)/1000.0;}
    private static String escape(String s){return s.replace("!","!!").replace("%","!%").replace("_","!_");}
    private static List<String> split(String s){return s==null||s.isBlank()?List.of():List.of(s.split(","));}
    private static void bad(String s){throw new ResponseStatusException(BAD_REQUEST,s);}
    public record Settings(boolean marketplaceListed,Double latitude,Double longitude,String city,String district,String coverUrl,boolean pickupEnabled,boolean shippingEnabled,long firstShippingCents,long extraShippingCents,List<String> excludedProvinces,List<String> pickupOnlySkus,int version){}
    public record NearbyStore(long id,long tenantId,String storeName,String address,String businessHours,String city,String district,String coverUrl,boolean pickupEnabled,boolean shippingEnabled,Double distanceKm){}
}
