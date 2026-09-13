import http from 'k6/http';
import { check, fail, sleep } from 'k6';

export const options = {
  scenarios: { business_flow: { executor: 'constant-vus', vus: Number(__ENV.VUS || 2), duration: __ENV.DURATION || '30s', gracefulStop: '10s' } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<1000', 'p(99)<2000'], checks: ['rate>0.99'] },
};

const base = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const adminUser = __ENV.ADMIN_USERNAME || __ENV.BOOTSTRAP_ADMIN_USERNAME || 'platform-admin';
const adminPassword = __ENV.ADMIN_PASSWORD || __ENV.BOOTSTRAP_ADMIN_PASSWORD || 'change-me-platform-admin';
const jsonHeaders = { 'Content-Type': 'application/json' };
function body(response) {
  if (!response.body) return null;
  // Snowflake IDs exceed JavaScript's safe integer range; preserve them as strings.
  const safeJson = response.body.replace(/("(?:id|[A-Za-z]+Id)"\s*:\s*)(\d{16,})/g, '$1"$2"');
  const parsed = JSON.parse(safeJson);
  return parsed.data || parsed;
}
function requireStatus(response, expected, name) {
  if (!check(response, { [`${name} status ${expected}`]: (r) => r.status === expected })) fail(`${name} failed: status=${response.status} body=${response.body}`);
  return body(response);
}
function post(path, payload, token, tag, expected = 200) {
  const headers = token ? { ...jsonHeaders, Authorization: `Bearer ${token}` } : jsonHeaders;
  return requireStatus(http.post(`${base}${path}`, JSON.stringify(payload), { headers, tags: { endpoint: tag } }), expected, tag);
}
function put(path, payload, token, tag) {
  return requireStatus(http.put(`${base}${path}`, JSON.stringify(payload), { headers: { ...jsonHeaders, Authorization: `Bearer ${token}` }, tags: { endpoint: tag } }), 200, tag);
}
function get(path, token, tag) {
  return requireStatus(http.get(`${base}${path}`, { headers: { Authorization: `Bearer ${token}` }, tags: { endpoint: tag } }), 200, tag);
}

export function setup() {
  const suffix = `${Date.now()}`.slice(-10), mobile = `13${suffix}`, password = 'M6-Load!2026';
  const admin = post('/api/auth/v1/login', { username: adminUser, password: adminPassword }, null, 'login_platform');
  const application = post('/api/public/v1/tenant-applications', { merchantName: `M6压测-${suffix}`, contactName: '性能验收', contactMobile: mobile, planCode: 'ADVANCED', password }, null, 'tenant_apply', 201);
  const tenant = post(`/api/platform/v1/tenant-applications/${application.id}:approve`, null, admin.accessToken, 'tenant_approve');
  const owner = post('/api/auth/v1/login', { username: mobile, password }, null, 'login_merchant');
  const token = owner.accessToken;
  const store = post('/api/merchant/v1/stores', { storeCode: `LOAD-${suffix}`, storeName: 'M6压测门店', address: '性能环境', businessHours: '00:00-24:00' }, token, 'store_create');
  const category = post('/api/merchant/v1/categories', { categoryCode: `LOAD-${suffix}`, categoryName: '压测分类', sortOrder: 1 }, token, 'category_create');
  let product = post('/api/merchant/v1/products', { categoryId: category.id, spuCode: `LOAD-${suffix}`, productName: '压测商品' }, token, 'product_create');
  product = put(`/api/merchant/v1/products/${product.id}`, { categoryId: category.id, productName: product.productName, status: 'ACTIVE', version: product.version }, token, 'product_publish');
  let sku = post(`/api/merchant/v1/products/${product.id}/skus`, { skuCode: `LOAD-${suffix}`, skuName: '标准规格', basePriceCents: 2000, allowStorePrice: true }, token, 'sku_create');
  sku = put(`/api/merchant/v1/skus/${sku.id}`, { skuName: sku.skuName, basePriceCents: 2000, allowStorePrice: true, status: 'ACTIVE', version: sku.version }, token, 'sku_publish');
  put(`/api/merchant/v1/stores/${store.id}/products/${sku.id}`, { sellable: true, storePriceCents: 2000 }, token, 'store_product');
  post(`/api/merchant/v1/inventory/${sku.id}/adjust`, { storeId: store.id, quantityDelta: 100000, idempotencyKey: `LOAD-STOCK-${suffix}`, reason: 'M6压测' }, token, 'inventory_seed');
  const now = Date.now();
  const coupon = post('/api/merchant/v1/coupon-templates', { templateCode: `LOAD-${suffix}`, templateName: 'M6压测券', discountCents: 100, minSpendCents: 1000, totalQuantity: 100000, validFrom: new Date(now - 60000).toISOString(), validTo: new Date(now + 86400000).toISOString() }, token, 'coupon_create');
  const permissions = get('/api/merchant/v1/permissions', token, 'permissions');
  const permissionIds = permissions.filter((item) => ['merchant:store:view', 'merchant:order:verify'].includes(item.permissionCode)).map((item) => item.id);
  const role = post('/api/merchant/v1/roles', { code: `LOAD-VERIFY-${suffix}`, name: 'M6压测核销员' }, token, 'role_create');
  put(`/api/merchant/v1/roles/${role.id}/permissions`, { ids: permissionIds }, token, 'role_permissions');
  const verifierName = `load-verify-${suffix}`, verifierPassword = 'M6-Verifier!2026';
  const verifier = post('/api/merchant/v1/users', { username: verifierName, password: verifierPassword, displayName: 'M6压测核销员', mobile: null, dataScope: 'STORE_SELF' }, token, 'verifier_create');
  put(`/api/merchant/v1/users/${verifier.id}/roles`, { ids: [role.id] }, token, 'verifier_roles');
  post('/api/merchant/v1/employees', { userId: verifier.id, employeeNo: `LOAD-${suffix}`, employeeName: 'M6压测核销员', primaryStoreId: store.id, jobTitle: '核销员', storeIds: [store.id] }, token, 'verifier_employee');
  return { tenantId: tenant.id, username: mobile, password, verifierName, verifierPassword, storeId: store.id, skuId: sku.id, templateId: coupon.id };
}

export default function (fixture) {
  const unique = `${__VU}-${__ITER}-${Date.now()}`, mobile = `15${`${__VU}${__ITER}${Date.now()}`.slice(-9)}`;
  const login = post('/api/auth/v1/login', { username: fixture.username, password: fixture.password }, null, 'login');
  const verifierLogin = post('/api/auth/v1/login', { username: fixture.verifierName, password: fixture.verifierPassword }, null, 'login_verifier');
  const headers = { ...jsonHeaders, Authorization: `Bearer ${login.accessToken}` };
  const catalog = http.batch([
    ['GET', `${base}/api/consumer/v1/catalog/tenants/${fixture.tenantId}/stores`, null, { tags: { endpoint: 'catalog_stores' } }],
    ['GET', `${base}/api/consumer/v1/catalog/tenants/${fixture.tenantId}/stores/${fixture.storeId}/products`, null, { tags: { endpoint: 'catalog_products' } }],
  ]);
  check(catalog[0], { 'catalog stores succeeds': (r) => r.status === 200 });
  check(catalog[1], { 'catalog products succeeds': (r) => r.status === 200 });
  post('/api/consumer/v1/members', { tenantId: fixture.tenantId, mobile, memberName: '压测顾客' }, login.accessToken, 'member_register');
  const claimed = post(`/api/consumer/v1/coupons/${fixture.templateId}/claim`, { tenantId: fixture.tenantId, mobile, requestId: `CLAIM-${unique}` }, login.accessToken, 'coupon_claim');
  const order = post('/api/consumer/v1/orders', { tenantId: fixture.tenantId, storeId: fixture.storeId, customerName: '压测顾客', customerMobile: mobile, requestId: `ORDER-${unique}`, items: [{ skuId: fixture.skuId, quantity: 1 }], benefits: { couponId: claimed.id, pointsToUse: 0, storedValueToUseCents: 0 } }, null, 'order_create');
  const paid = post(`/api/consumer/v1/orders/${order.id}/simulate-payment`, { tenantId: fixture.tenantId, paymentRequestId: `PAY-${unique}` }, null, 'payment');
  post(`/api/merchant/v1/orders/${order.id}/verify`, { pickupCode: paid.pickupCode }, verifierLogin.accessToken, 'order_verify');
  const today = new Date().toISOString().slice(0, 10);
  const dashboard = http.get(`${base}/api/merchant/v1/analytics/dashboard?from=${today}&to=${today}`, { headers, tags: { endpoint: 'dashboard' } });
  check(dashboard, { 'dashboard succeeds': (r) => r.status === 200 });
  sleep(1);
}
