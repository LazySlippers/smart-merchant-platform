INSERT INTO permission (id, permission_code, permission_name, audience) VALUES
    (2210, 'merchant:product:view', '查看分类与商品', 'MERCHANT'),
    (2211, 'merchant:product:manage', '管理分类与商品', 'MERCHANT'),
    (2212, 'merchant:store-product:manage', '管理门店可售范围', 'MERCHANT'),
    (2213, 'merchant:price:manage', '管理品牌与门店价格', 'MERCHANT');

INSERT INTO role_permission (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_code='OWNER' AND r.platform_role=0 AND p.id BETWEEN 2210 AND 2213;
