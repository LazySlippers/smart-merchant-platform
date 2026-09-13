INSERT INTO permission (id, permission_code, permission_name, audience) VALUES
    (2201, 'merchant:store:view', '查看门店', 'MERCHANT'),
    (2202, 'merchant:store:manage', '管理门店', 'MERCHANT'),
    (2203, 'merchant:employee:view', '查看员工档案', 'MERCHANT'),
    (2204, 'merchant:employee:manage', '管理员工档案与任职门店', 'MERCHANT');

INSERT INTO role_permission (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id
FROM role r CROSS JOIN permission p
WHERE r.role_code = 'OWNER' AND r.platform_role = 0 AND p.id BETWEEN 2201 AND 2204;
