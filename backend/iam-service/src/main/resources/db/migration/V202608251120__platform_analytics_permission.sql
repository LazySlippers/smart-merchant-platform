INSERT INTO permission (id, permission_code, permission_name, audience) VALUES (2005, 'platform:analytics:view', '查看平台聚合经营指标', 'PLATFORM');
INSERT INTO role_permission(tenant_id,role_id,permission_id)
SELECT 0,r.id,2005 FROM role r WHERE r.tenant_id=0 AND r.role_code='PLATFORM_SUPER_ADMIN';
