INSERT INTO permission (id, permission_code, permission_name, audience) VALUES
 (2220, 'merchant:inventory:view', '查看库存与流水', 'MERCHANT'),
 (2221, 'merchant:inventory:manage', '调整与盘点库存', 'MERCHANT'),
 (2222, 'merchant:transfer:manage', '创建与执行调拨', 'MERCHANT'),
 (2223, 'merchant:transfer:approve', '审批跨店调拨', 'MERCHANT');

INSERT INTO role_permission (tenant_id, role_id, permission_id)
SELECT r.tenant_id,r.id,p.id FROM role r CROSS JOIN permission p
WHERE r.role_code='OWNER' AND r.platform_role=0 AND p.id BETWEEN 2220 AND 2223;
