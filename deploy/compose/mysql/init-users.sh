#!/bin/sh
set -eu

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS 'saas_tenant'@'%' IDENTIFIED BY '${MYSQL_APP_PASSWORD}';
CREATE USER IF NOT EXISTS 'saas_iam'@'%' IDENTIFIED BY '${MYSQL_APP_PASSWORD}';
CREATE USER IF NOT EXISTS 'saas_merchant'@'%' IDENTIFIED BY '${MYSQL_APP_PASSWORD}';
CREATE USER IF NOT EXISTS 'saas_member'@'%' IDENTIFIED BY '${MYSQL_APP_PASSWORD}';
CREATE USER IF NOT EXISTS 'saas_trade'@'%' IDENTIFIED BY '${MYSQL_APP_PASSWORD}';
GRANT ALL ON saas_tenant.* TO 'saas_tenant'@'%';
GRANT ALL ON saas_iam.* TO 'saas_iam'@'%';
GRANT ALL ON saas_merchant.* TO 'saas_merchant'@'%';
GRANT ALL ON saas_member.* TO 'saas_member'@'%';
GRANT ALL ON saas_trade.* TO 'saas_trade'@'%';
FLUSH PRIVILEGES;
SQL
