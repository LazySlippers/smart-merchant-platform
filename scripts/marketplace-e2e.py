"""Local-only marketplace/shipping acceptance. Creates isolated tenants and suspends them afterward."""
import json
import time
import uuid
from datetime import date, timedelta
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from urllib.parse import urlencode

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:8080'
settings = dict(line.split('=', 1) for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines() if line and not line.startswith('#') and '=' in line)
suffix = str(int(time.time()))
prefix = '邮寄验收-' + suffix
tenants = []
checks = []
password = 'Acceptance!' + uuid.uuid4().hex[:16]

def api(method, path, body=None, token=None, expected=(200,)):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with urlopen(request, timeout=30) as response:
            code, data = response.status, response.read()
    except HTTPError as error:
        code, data = error.code, error.read()
    if code not in expected:
        raise AssertionError(f'{method} {path} returned {code}, expected {expected}')
    return json.loads(data) if data else None

def login(username):
    return api('POST', '/api/auth/v1/login', {'username': username, 'password': password})['accessToken']

admin = api('POST', '/api/auth/v1/login', {'username': settings['BOOTSTRAP_ADMIN_USERNAME'], 'password': settings['BOOTSTRAP_ADMIN_PASSWORD']})['accessToken']

def tenant(index):
    mobile = '139' + str(int(suffix[-8:]) + index).zfill(8)
    application = api('POST', '/api/public/v1/tenant-applications', {'merchantName': prefix + str(index), 'contactName': '验收', 'contactMobile': mobile, 'planCode': 'STANDARD', 'password': password}, expected=(201,))
    result = api('POST', f'/api/platform/v1/tenant-applications/{application["id"]}:approve', token=admin)
    tenants.append(result['id'])
    return result['id'], login(mobile)

def store(owner, code, lat):
    s = api('POST', '/api/merchant/v1/stores', {'storeCode': code + suffix, 'storeName': prefix + code, 'address': '浙江省杭州市西湖区验收路18号', 'businessHours': '09:00-21:00'}, owner)
    config = api('GET', f'/api/merchant/v1/stores/{s["id"]}/storefront', token=owner)
    config.update(latitude=lat, longitude=120.15, city='杭州市', district='西湖区', shippingEnabled=True, firstShippingCents=800, extraShippingCents=200, excludedProvinces=['西藏自治区'])
    api('PUT', f'/api/merchant/v1/stores/{s["id"]}/storefront', config, owner)
    return s['id']

def worker(owner, store_id, label):
    permissions = api('GET', '/api/merchant/v1/permissions', token=owner)
    ids = [p['id'] for p in permissions if p['permissionCode'] in ('merchant:store:view', 'merchant:order:view', 'merchant:order:verify')]
    role = api('POST', '/api/merchant/v1/roles', {'code': label + suffix, 'name': '发货验收员'}, owner)
    api('PUT', f'/api/merchant/v1/roles/{role["id"]}/permissions', {'ids': ids}, owner)
    username = label + suffix
    user = api('POST', '/api/merchant/v1/users', {'username': username, 'password': password, 'displayName': '验收店员', 'dataScope': 'STORE_SELF'}, owner)
    api('PUT', f'/api/merchant/v1/users/{user["id"]}/roles', {'ids': [role['id']]}, owner)
    api('POST', '/api/merchant/v1/employees', {'userId': user['id'], 'employeeNo': username, 'employeeName': '验收店员', 'primaryStoreId': store_id, 'storeIds': [store_id]}, owner)
    return login(username)

try:
    t1, owner = tenant(1)
    t2, other_owner = tenant(2)
    a, b, c = store(owner, 'A', 30.25), store(owner, 'B', 30.26), store(other_owner, 'C', 31.25)
    directory = '/api/consumer/v1/catalog/stores?' + urlencode({'latitude': 30.25, 'longitude': 120.15, 'search': prefix})
    rows = api('GET', directory)
    assert [s['id'] for s in rows] == [a, b, c]
    assert {s['tenantId'] for s in rows} == {t1, t2}
    api('GET', f'/api/merchant/v1/stores/{c}/storefront', token=owner, expected=(403,))
    checks.append('multi-tenant directory, distance ordering and scoped settings')

    category = api('POST', '/api/merchant/v1/categories', {'categoryCode': 'C' + suffix, 'categoryName': '日用百货', 'sortOrder': 1}, owner)
    product = api('POST', '/api/merchant/v1/products/with-sku', {'categoryId': category['id'], 'productCode': 'P' + suffix, 'productName': '邮寄验收商品', 'skuCode': 'S' + suffix, 'skuName': '标准件', 'priceCents': 1800}, owner)
    sku = product['skus'][0]['sku']['id']
    api('PUT', f'/api/merchant/v1/stores/{a}/products/{sku}', {'sellable': True, 'storePriceCents': None}, owner)
    api('POST', f'/api/merchant/v1/inventory/{sku}/adjust', {'storeId': a, 'quantityDelta': 10, 'idempotencyKey': 'INIT-' + suffix, 'reason': '独立验收库存'}, owner)
    mobile = '137' + suffix[-8:]
    consumer = api('POST', '/api/consumer/v1/auth/register', {'tenantId': t1, 'mobile': mobile, 'memberName': '验收顾客', 'password': password})['accessToken']
    other_consumer = api('POST', '/api/consumer/v1/auth/register', {'tenantId': t2, 'mobile': mobile, 'memberName': '另一商家顾客', 'password': password})['accessToken']
    body = {'tenantId': t1, 'storeId': a, 'customerName': '验收顾客', 'customerMobile': mobile, 'requestId': 'SHIP-' + suffix, 'items': [{'skuId': sku, 'quantity': 3}], 'delivery': {'method': 'SHIPPING', 'name': '收件验收人', 'phone': mobile, 'province': '浙江省', 'address': '浙江省杭州市西湖区验收路18号'}}
    quote = api('POST', '/api/consumer/v1/orders/quote', body, consumer)
    assert quote['shippingFeeCents'] == 1200 and quote['payableAmountCents'] == 6600
    excluded = {**body, 'delivery': {**body['delivery'], 'province': '西藏自治区', 'address': '西藏自治区拉萨市验收路18号'}}
    api('POST', '/api/consumer/v1/orders/quote', excluded, consumer, expected=(400,))
    order = api('POST', '/api/consumer/v1/orders', body, consumer)
    assert api('POST', '/api/consumer/v1/orders', body, consumer)['id'] == order['id']
    assert order['delivery']['shippingFeeCents'] == 1200
    api('POST', f'/api/consumer/v1/orders/{order["id"]}/receive', {}, other_consumer, expected=(404,))
    paid = api('POST', f'/api/consumer/v1/orders/{order["id"]}/simulate-payment', {'tenantId': t1, 'paymentRequestId': 'PAY-' + suffix}, consumer)
    assert paid['status'] == 'PENDING_SHIPMENT' and paid['pickupCode'] is None
    analytics_path = '/api/merchant/v1/analytics/dashboard?' + urlencode({'from': str(date.today() - timedelta(days=1)), 'to': str(date.today() + timedelta(days=1)), 'storeId': a})
    for attempt in range(20):
        summary = api('GET', analytics_path, token=owner)['summary']
        if summary['paidOrders'] == 1 and summary['grossRevenueCents'] == 6600:
            break
        time.sleep(1)
    assert summary['paidOrders'] == 1 and summary['grossRevenueCents'] == 6600, 'Paid order must count toward dashboard revenue before shipment'
    checks.append('paid order contributes 6600 cents to dashboard revenue before shipment')
    checks.append('trusted freight, prohibited region, order/payment snapshots and identity isolation')

    wa, wb = worker(owner, a, 'shipA'), worker(owner, b, 'shipB')
    shipment = {'carrier': '验收快递', 'trackingNo': 'TEST' + suffix}
    api('POST', f'/api/merchant/v1/orders/{order["id"]}/ship', shipment, wb, expected=(403,))
    shipped = api('POST', f'/api/merchant/v1/orders/{order["id"]}/ship', shipment, wa)
    assert shipped['status'] == 'SHIPPED'
    assert api('POST', f'/api/merchant/v1/orders/{order["id"]}/ship', shipment, wa)['status'] == 'SHIPPED'
    assert api('POST', f'/api/consumer/v1/orders/{order["id"]}/receive', {}, consumer)['status'] == 'COMPLETED'
    api('POST', f'/api/consumer/v1/orders/{order["id"]}/receive', {}, consumer)
    entries = api('GET', f'/api/merchant/v1/settlements/finance-ledger?storeId={a}', token=owner)
    assert len(entries) == 2
    assert {e['businessType']: e['amountCents'] for e in entries} == {'ORDER': 5400, 'SHIPPING_FEE': 1200}
    api('POST', f'/api/merchant/v1/orders/{order["id"]}/refund', {'requestId': 'REFUND-' + suffix, 'reason': '验收恢复库存'}, owner)
    stock = api('GET', f'/api/merchant/v1/inventory?storeId={a}', token=owner)
    assert stock[0]['actualQuantity'] == 10 and stock[0]['reservedQuantity'] == 0
    checks.append('own-store shipping, receipt replay, freight ledger and full refund stock restoration')

    api('POST', f'/api/platform/v1/tenants/{t2}:suspend', token=admin)
    tenants.remove(t2)
    assert all(s['tenantId'] != t2 for s in api('GET', directory))
    checks.append('suspended tenant excluded from public directory')
    print(json.dumps({'result': 'PASSED', 'tenantId': str(t1), 'orderId': str(order['id']), 'checks': checks}, ensure_ascii=False))
finally:
    for tenant_id in tenants:
        api('POST', f'/api/platform/v1/tenants/{tenant_id}:suspend', token=admin)
