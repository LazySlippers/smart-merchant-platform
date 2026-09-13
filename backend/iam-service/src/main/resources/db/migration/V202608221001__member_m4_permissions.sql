INSERT INTO permission (id, permission_code, permission_name, audience) VALUES
 (2240, 'merchant:member:view', '查看会员', 'MERCHANT'),
 (2241, 'merchant:member:manage', '管理会员、积分与储值', 'MERCHANT'),
 (2242, 'merchant:coupon:view', '查看优惠券模板', 'MERCHANT'),
 (2243, 'merchant:coupon:manage', '管理优惠券模板', 'MERCHANT'),
 (2244, 'merchant:order:refund', '发起订单退款', 'MERCHANT');
INSERT INTO role_permission (tenant_id, role_id, permission_id)
SELECT r.tenant_id,r.id,p.id FROM role r CROSS JOIN permission p WHERE r.role_code='OWNER' AND r.platform_role=0 AND p.id BETWEEN 2240 AND 2244;
