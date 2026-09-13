INSERT INTO permission (id, permission_code, permission_name, audience) VALUES
 (2230, 'merchant:order:view', '查看门店订单', 'MERCHANT'),
 (2231, 'merchant:order:verify', '核销自提订单', 'MERCHANT'),
 (2232, 'merchant:finance:view', '查看财务流水', 'MERCHANT');

INSERT INTO role_permission (tenant_id, role_id, permission_id)
SELECT r.tenant_id,r.id,p.id FROM role r CROSS JOIN permission p
WHERE r.role_code='OWNER' AND r.platform_role=0 AND p.id BETWEEN 2230 AND 2232;
