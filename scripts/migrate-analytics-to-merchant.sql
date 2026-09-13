-- Stop all event producers and the old analytics service first.
-- Run after merchant Flyway migration, with a DB administrator account.
-- Destination analytics tables must be empty; any duplicate is an error.
SET autocommit = 0;
START TRANSACTION;
INSERT INTO saas_merchant.analytics_inbox SELECT * FROM saas_analytics.analytics_inbox;
INSERT INTO saas_merchant.analytics_order_fact SELECT * FROM saas_analytics.analytics_order_fact;
INSERT INTO saas_merchant.analytics_order_item_fact SELECT * FROM saas_analytics.analytics_order_item_fact;
INSERT INTO saas_merchant.analytics_member_event_fact SELECT * FROM saas_analytics.analytics_member_event_fact;
INSERT INTO saas_merchant.analytics_metric_minute SELECT * FROM saas_analytics.analytics_metric_minute;
INSERT INTO saas_merchant.analytics_metric_daily SELECT * FROM saas_analytics.analytics_metric_daily;
COMMIT;
