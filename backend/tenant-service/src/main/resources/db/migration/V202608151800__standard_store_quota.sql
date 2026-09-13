UPDATE plan_feature
SET quota_value = 2, updated_at = CURRENT_TIMESTAMP
WHERE feature_code = 'merchant.store'
  AND plan_id = (SELECT id FROM plan WHERE plan_code = 'STANDARD');
