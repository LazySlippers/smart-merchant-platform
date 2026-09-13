package com.smartmerchant.saas.merchant.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.LinkedHashSet;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ConsumerCatalogService {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final boolean runtimeStateEnabled;

    public ConsumerCatalogService(JdbcTemplate jdbc, StringRedisTemplate redis,
                                  @Value("${saas.security.public-catalog.runtime-state.enabled:false}") boolean runtimeStateEnabled) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.runtimeStateEnabled = runtimeStateEnabled;
    }

    public List<StoreView> stores(long tenantId) {
        requireActiveTenant(tenantId);
        return jdbc.query("""
                SELECT id, store_code, store_name, address, business_hours
                  FROM store
                 WHERE tenant_id = ? AND status = 'ACTIVE' AND marketplace_listed=TRUE
                 ORDER BY id
                """, (rs, rowNum) -> new StoreView(
                rs.getLong("id"), rs.getString("store_code"), rs.getString("store_name"),
                rs.getString("address"), rs.getString("business_hours")), tenantId);
    }

    public StoreCatalogView products(long tenantId, long storeId) {
        requireActiveTenant(tenantId);
        var stores = jdbc.query("""
                SELECT id, store_code, store_name, address, business_hours
                  FROM store
                 WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND marketplace_listed=TRUE
                """, (rs, rowNum) -> new StoreView(
                rs.getLong("id"), rs.getString("store_code"), rs.getString("store_name"),
                rs.getString("address"), rs.getString("business_hours")), tenantId, storeId);
        if (stores.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Store not found");
        }

        var products = jdbc.query("""
                SELECT c.id AS category_id, c.category_name,
                       p.id AS spu_id, p.product_name, p.description, p.image_url,
                       s.id AS sku_id, s.sku_code, s.sku_name, s.spec_json, s.barcode,
                       COALESCE(sp.price_cents, s.base_price_cents) AS effective_price_cents,
                       COALESCE(i.available_quantity, 0) AS available_quantity
                  FROM store_product sale
                  JOIN product_sku s
                    ON s.tenant_id = sale.tenant_id AND s.id = sale.sku_id AND s.status = 'ACTIVE'
                  JOIN product_spu p
                    ON p.tenant_id = s.tenant_id AND p.id = s.spu_id AND p.status = 'ACTIVE'
                  JOIN category c
                    ON c.tenant_id = p.tenant_id AND c.id = p.category_id AND c.status = 'ACTIVE'
             LEFT JOIN store_price sp
                    ON sp.tenant_id = sale.tenant_id AND sp.store_id = sale.store_id AND sp.sku_id = sale.sku_id
             LEFT JOIN inventory i
                    ON i.tenant_id = sale.tenant_id AND i.store_id = sale.store_id AND i.sku_id = sale.sku_id
                 WHERE sale.tenant_id = ? AND sale.store_id = ? AND sale.sellable = 1
                 ORDER BY c.sort_order, c.id, p.id, s.id
                """, (rs, rowNum) -> {
            long available = rs.getLong("available_quantity");
            return new ProductView(
                    rs.getLong("category_id"), rs.getString("category_name"),
                    rs.getLong("spu_id"), rs.getString("product_name"), rs.getString("description"),
                    rs.getString("image_url"), rs.getLong("sku_id"), rs.getString("sku_code"),
                    rs.getString("sku_name"), rs.getString("spec_json"), rs.getString("barcode"),
                    rs.getLong("effective_price_cents"), available, available > 0);
        }, tenantId, storeId);
        return new StoreCatalogView(stores.getFirst(), products);
    }

    public List<QuoteItem> quote(long tenantId, long storeId, List<QuoteRequestItem> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Order requires items");
        }
        var ids = new LinkedHashSet<Long>();
        for (var item : items) {
            if (item.skuId() <= 0 || item.quantity() <= 0 || !ids.add(item.skuId())) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid or duplicate order item");
            }
        }
        var result = new java.util.ArrayList<QuoteItem>();
        for (var item : items) {
            var rows = jdbc.query("""
                    SELECT p.product_name, p.image_url, s.id AS sku_id, s.sku_code, s.sku_name,
                           s.spec_json, COALESCE(price.price_cents, s.base_price_cents) AS price_cents,
                           COALESCE(i.available_quantity, 0) AS available_quantity
                      FROM store st
                      JOIN store_product sale ON sale.tenant_id=st.tenant_id AND sale.store_id=st.id AND sale.sellable=1
                      JOIN product_sku s ON s.tenant_id=sale.tenant_id AND s.id=sale.sku_id AND s.status='ACTIVE'
                      JOIN product_spu p ON p.tenant_id=s.tenant_id AND p.id=s.spu_id AND p.status='ACTIVE'
                      JOIN category c ON c.tenant_id=p.tenant_id AND c.id=p.category_id AND c.status='ACTIVE'
                 LEFT JOIN store_price price ON price.tenant_id=sale.tenant_id AND price.store_id=sale.store_id AND price.sku_id=sale.sku_id
                 LEFT JOIN inventory i ON i.tenant_id=sale.tenant_id AND i.store_id=sale.store_id AND i.sku_id=sale.sku_id
                     WHERE st.tenant_id=? AND st.id=? AND st.status='ACTIVE' AND s.id=?
                    """, (rs, rowNum) -> new QuoteItem(rs.getLong("sku_id"), rs.getString("sku_code"),
                    rs.getString("product_name"), rs.getString("sku_name"), rs.getString("spec_json"),
                    rs.getString("image_url"), rs.getLong("price_cents"), item.quantity(),
                    rs.getLong("available_quantity")), tenantId, storeId, item.skuId());
            if (rows.isEmpty()) {
                throw new ResponseStatusException(NOT_FOUND, "SKU is not sellable in this store: " + item.skuId());
            }
            result.add(rows.getFirst());
        }
        return List.copyOf(result);
    }

    public boolean isActiveTenant(long tenantId) {
        if (!runtimeStateEnabled) return true;
        var state = redis.opsForValue().get("saas:tenant:status:" + tenantId);
        return state != null && state.startsWith("ACTIVE:");
    }

    public void requireActiveTenant(long tenantId) {
        if (!isActiveTenant(tenantId)) {
            throw new ResponseStatusException(NOT_FOUND, "Tenant not found");
        }
    }

    public record StoreView(long id, String storeCode, String storeName, String address, String businessHours) {}

    public record StoreCatalogView(StoreView store, List<ProductView> products) {}

    public record ProductView(long categoryId, String categoryName, long spuId, String productName,
                              String description, String imageUrl, long skuId, String skuCode,
                              String skuName, String specJson, String barcode, long effectivePriceCents,
                              long availableQuantity, boolean selectable) {}
    public record QuoteRequestItem(long skuId, long quantity) {}
    public record QuoteItem(long skuId, String skuCode, String productName, String skuName,
                            String specJson, String imageUrl, long priceCents, long quantity,
                            long availableQuantity) {}
}
