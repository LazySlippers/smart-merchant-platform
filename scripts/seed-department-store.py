"""Create the single department store through normal application APIs. Safe to resume."""
import json, pathlib, secrets, urllib.request, urllib.error
ROOT=pathlib.Path(__file__).resolve().parents[1]
BASE='http://127.0.0.1:8080'
STATE=ROOT/'runtime-logs/mall-seed.json'
CREDS=ROOT/'runtime-logs/mall-accounts.json'
def api(method,path,body=None,token=None):
    request=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),headers={'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})},method=method)
    try:
        with urllib.request.urlopen(request,timeout=30) as r:
            data=r.read();return json.loads(data) if data else None
    except urllib.error.HTTPError as e:raise RuntimeError(f'{method} {path}: {e.code} {e.read().decode()}') from None
def save():STATE.write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')
env=dict(line.strip().split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
if CREDS.exists():credentials=json.loads(CREDS.read_text(encoding='utf-8'))
else:
    credentials={'mallName':'悦享百货','owner':{'username':'13900000001','password':'Mall!'+secrets.token_urlsafe(12)},'store':{'username':'yuexiang-store','password':'Store!'+secrets.token_urlsafe(12)}}
    CREDS.write_text(json.dumps(credentials,ensure_ascii=False,indent=2),encoding='utf-8')
if not credentials.get('second'):
    credentials['second']={'mallName':'森野花房','owner':{'username':'13900000002','password':'Garden!'+secrets.token_urlsafe(12)},'store':{'username':'senye-store','password':'Store!'+secrets.token_urlsafe(12)}}
    CREDS.write_text(json.dumps(credentials,ensure_ascii=False,indent=2),encoding='utf-8')
state=json.loads(STATE.read_text(encoding='utf-8')) if STATE.exists() else {}
def admin_token():return api('POST','/api/auth/v1/login',{'username':env['BOOTSTRAP_ADMIN_USERNAME'],'password':env['BOOTSTRAP_ADMIN_PASSWORD']})['accessToken']
if not state.get('tenantId'):
    admin=admin_token()
    existing=api('GET','/api/platform/v1/tenants',token=admin)
    if existing:raise RuntimeError('Expected a clean tenant database; refusing to create another mall.')
    app=api('POST','/api/public/v1/tenant-applications',{'merchantName':credentials['mallName'],'contactName':'商场管理员','contactMobile':credentials['owner']['username'],'planCode':'STANDARD','password':credentials['owner']['password']})
    tenant=api('POST',f"/api/platform/v1/tenant-applications/{app['id']}:approve",token=admin)
    state={'tenantId':str(tenant['id']),'mallName':credentials['mallName'],'products':[]};save()
token=api('POST','/api/auth/v1/login',credentials['owner'])['accessToken']
if not state.get('storeId'):
    store=api('POST','/api/merchant/v1/stores',{'storeCode':'YX-001','storeName':'悦享百货 · 中心店','address':'中心店一层服务台（请在后台补充实际地址）','businessHours':'09:00–22:00'},token)
    state['storeId']=str(store['id']);save()
storeId=state['storeId']
accounts=api('GET','/api/merchant/v1/stores/accounts',token=token)
if not accounts:api('POST',f'/api/merchant/v1/stores/{storeId}/account',{**credentials['store'],'displayName':'中心店店长'},token)
else:
    credentials['store']['username']=accounts[0]['username']
    api('PUT',f'/api/merchant/v1/stores/{storeId}/account/password',{'password':credentials['store']['password']},token)
settings=api('GET','/api/merchant/v1/consumer-settings',token=token)
api('PUT','/api/merchant/v1/consumer-settings',{'displayName':'悦享百货','logoUrl':'','themeColor':'#c45b3f','headline':'把生活，选成喜欢的样子。','contactPhone':'','version':settings['version']},token)
storefront=api('GET',f'/api/merchant/v1/stores/{storeId}/storefront',token=token)
api('PUT',f'/api/merchant/v1/stores/{storeId}/storefront',{**storefront,'marketplaceListed':True,'latitude':30.2741,'longitude':120.1551,'city':'杭州市','district':'拱墅区','coverUrl':'/images/department-store-hero.png','pickupEnabled':True,'shippingEnabled':True,'firstShippingCents':800,'extraShippingCents':200,'excludedProvinces':[],'pickupOnlySkus':[]},token)

# name, price in yuan, product illustration, default specification, description
catalog=[
 ('食品饮料','food',[('每日混合坚果',39.9,'nuts','500g / 罐','多种坚果搭配，办公室与家庭分享装。'),('精品挂耳咖啡',49.9,'coffee','10 包 / 盒','独立小包装，随时享受醇香咖啡。'),('燕麦谷物早餐',29.9,'oats','600g / 袋','谷物与果干搭配，开启元气早晨。')]),
 ('家居日用','home',[('柔软纯棉浴巾',59,'towel','米白 / 70×140cm','蓬松触感，日常沐浴与旅行皆宜。'),('日常陶瓷马克杯',39,'mug','奶油白 / 350ml','简洁弧形杯身，握感舒适。'),('原木收纳托盘',45,'tray','天然木色 / 中号','桌面小物整齐收纳，让日常更有序。')]),
 ('个护美妆','beauty',[('温和洁面乳',69,'cleanser','150ml / 支','轻柔洁净，适合日常面部清洁。'),('保湿身体乳',89,'lotion','300ml / 瓶','滋润肤感，轻盈质地。'),('清新香氛护手霜',35,'cream','50g / 支','便携装设计，日常随身呵护。')]),
 ('服饰配件','fashion',[('基础纯棉短袖',89,'shirt','米白 / M','舒适纯棉面料，简洁版型，轻松搭配。'),('轻盈帆布托特包',69,'tote','原色 / 单肩款','通勤与购物随行，收纳日常所需。'),('轻量遮阳帽',49,'hat','沙色 / 可调节','简约日常款，适合出行搭配。')]),
 ('数码家电','digital',[('无线头戴式耳机',239,'headphones','雾蓝 / 标准版','轻量佩戴，无线连接，享受个人音乐时光。'),('便携充电宝',129,'powerbank','奶油白 / 10000mAh','双接口设计，为日常出行补充电量。'),('恒温电热水壶',169,'kettle','米白 / 1.5L','日常烧水好帮手，简约家居外观。')]),
 ('运动户外','sport',[('轻弹瑜伽垫',99,'mat','鼠尾草绿 / 6mm','舒适支撑，居家拉伸与瑜伽练习。'),('随行运动水杯',79,'bottle','雾蓝 / 650ml','轻便随行，通勤与运动场景适用。'),('训练弹力带套装',45,'bands','三条 / 组合装','多种训练强度，便携收纳。')]),
 ('母婴玩具','kids',[('童趣积木套装',119,'blocks','36 粒 / 套','彩色造型组合，适合亲子互动。'),('亲肤婴儿纱布巾',39,'cloth','三条 / 组合装','柔软细腻，日常清洁替换方便。'),('软萌小熊玩偶',79,'bear','奶茶棕 / 30cm','柔软填充，陪伴每一个温暖日常。')]),
 ('文具办公','office',[('日常计划笔记本',29,'notebook','A5 / 横线款','记录计划与灵感，简洁耐看。'),('顺滑中性笔套装',19.9,'pens','黑色 / 5 支','流畅书写，办公学习常备。'),('简约桌面台灯',129,'lamp','米白 / 标准款','柔和照明，为阅读与工作留一盏灯。')]),
]
existingCategories={c['categoryCode']:c for c in api('GET','/api/merchant/v1/categories',token=token)}
existingProducts={p['spu']['spuCode']:p['spu'] for p in api('GET','/api/merchant/v1/products',token=token)}
for index,(category,code,items) in enumerate(catalog):
    cat=existingCategories.get(code) or api('POST','/api/merchant/v1/categories',{'categoryCode':code,'categoryName':category,'sortOrder':index},token)
    for name,price,art,spec,description in items:
        productCode='YX-'+art
        if any(p['code']==productCode for p in state['products']):continue
        image='/images/products/'+art+'.svg'
        spu=existingProducts.get(productCode) or api('POST','/api/merchant/v1/products',{'categoryId':cat['id'],'spuCode':productCode,'productName':name,'description':description,'imageUrl':image},token)
        spu=api('PUT',f"/api/merchant/v1/products/{spu['id']}",{'categoryId':cat['id'],'productName':name,'description':description,'imageUrl':image,'status':'ACTIVE','version':spu['version']},token)
        variants=[spec]+(['米白 / L','米白 / XL'] if art=='shirt' else ['陶土红 / 350ml'] if art=='mug' else [])
        skuIds=[]
        for variantIndex,variant in enumerate(variants):
            sku=api('POST',f"/api/merchant/v1/products/{spu['id']}/skus",{'skuCode':productCode+'-'+str(variantIndex+1),'skuName':variant,'specJson':json.dumps({'规格':variant},ensure_ascii=False),'basePriceCents':round(price*100),'allowStorePrice':True},token)
            sku=api('PUT',f"/api/merchant/v1/skus/{sku['id']}",{'skuName':variant,'specJson':sku.get('specJson'),'basePriceCents':round(price*100),'allowStorePrice':True,'status':'ACTIVE','version':sku['version']},token)
            api('PUT',f"/api/merchant/v1/stores/{storeId}/products/{sku['id']}",{'sellable':True,'storePriceCents':round(price*100)},token)
            api('POST',f"/api/merchant/v1/inventory/{sku['id']}/adjust",{'storeId':storeId,'quantityDelta':100,'idempotencyKey':'MALL-INITIAL-'+str(sku['id']),'reason':'百货商场初始样品库存'},token)
            skuIds.append(str(sku['id']))
        state['products'].append({'code':productCode,'name':name,'category':category,'spuId':str(spu['id']),'skuIds':skuIds,'price':price,'image':image});save()
if not state.get('couponId'):
    coupon=api('POST','/api/merchant/v1/coupon-templates',{'templateCode':'MALL-WELCOME','templateName':'新客满 99 减 10 元','discountCents':1000,'minSpendCents':9900,'totalQuantity':10000,'validFrom':'2026-01-01T00:00:00','validTo':'2027-12-31T23:59:59'},token)
    api('PUT',f"/api/merchant/v1/coupon-templates/{coupon['id']}/status",{'status':'ACTIVE','version':coupon['version']},token)
    state['couponId']=str(coupon['id']);save()

# A second, independently owned public tenant. Its garden assortment, address and
# green campaign cover intentionally differ from the retained department store.
second_credentials=credentials['second']
second=state.setdefault('second',{'mallName':second_credentials['mallName'],'products':[]})
if not second.get('tenantId'):
    app=api('POST','/api/public/v1/tenant-applications',{'merchantName':second_credentials['mallName'],'contactName':'花房主理人','contactMobile':second_credentials['owner']['username'],'planCode':'STANDARD','password':second_credentials['owner']['password']})
    tenant=api('POST',f"/api/platform/v1/tenant-applications/{app['id']}:approve",token=admin_token())
    second['tenantId']=str(tenant['id']);save()
second_token=api('POST','/api/auth/v1/login',second_credentials['owner'])['accessToken']
if not second.get('storeId'):
    store=api('POST','/api/merchant/v1/stores',{'storeCode':'SY-001','storeName':'森野花房 · 湖滨店','address':'浙江省杭州市上城区延安路258号湖滨步行街北区','businessHours':'10:00–20:30'},second_token)
    second['storeId']=str(store['id']);save()
second_store_id=second['storeId']
second_accounts=api('GET','/api/merchant/v1/stores/accounts',token=second_token)
if not second_accounts:api('POST',f'/api/merchant/v1/stores/{second_store_id}/account',{**second_credentials['store'],'displayName':'湖滨花房店长'},second_token)
else:
    second_credentials['store']['username']=second_accounts[0]['username']
    api('PUT',f'/api/merchant/v1/stores/{second_store_id}/account/password',{'password':second_credentials['store']['password']},second_token)
second_settings=api('GET','/api/merchant/v1/consumer-settings',token=second_token)
api('PUT','/api/merchant/v1/consumer-settings',{'displayName':'森野花房','logoUrl':'','themeColor':'#365f48','headline':'把一片自然，带回日常。','contactPhone':'','version':second_settings['version']},second_token)
second_storefront=api('GET',f'/api/merchant/v1/stores/{second_store_id}/storefront',token=second_token)
api('PUT',f'/api/merchant/v1/stores/{second_store_id}/storefront',{**second_storefront,'marketplaceListed':True,'latitude':30.2528,'longitude':120.1645,'city':'杭州市','district':'上城区','coverUrl':'/images/matcha-campaign.png','pickupEnabled':True,'shippingEnabled':False,'firstShippingCents':0,'extraShippingCents':0,'excludedProvinces':[],'pickupOnlySkus':[]},second_token)

garden_catalog=[
 ('鲜花花束','bouquet',[('晨光向日葵花束',128,'flower','6 枝 / 牛皮纸包装','明亮向日葵与尤加利叶搭配，适合祝福与日常陈设。'),('柔雾玫瑰花束',168,'rose','11 枝 / 香槟粉','柔和低饱和配色，花店当日手工包扎。')]),
 ('绿植盆栽','plants',[('龟背竹桌面盆栽',79,'plant','含陶盆 / 小号','耐阴易养护，为桌面添一抹自然绿意。'),('香草种植组合',59,'herb','罗勒与薄荷 / 2 盆','适合阳台栽种的入门香草组合。')]),
 ('花器园艺','garden',[('手作釉面花瓶',98,'vase','苔绿色 / 22cm','窑变釉面，每只纹理略有不同。'),('家庭园艺工具组',89,'garden-tools','三件套 / 墨绿色','包含移植铲、松土耙与修枝剪。')]),
]
second_categories={c['categoryCode']:c for c in api('GET','/api/merchant/v1/categories',token=second_token)}
second_products={p['spu']['spuCode']:p['spu'] for p in api('GET','/api/merchant/v1/products',token=second_token)}
for index,(category,code,items) in enumerate(garden_catalog):
    cat=second_categories.get(code) or api('POST','/api/merchant/v1/categories',{'categoryCode':code,'categoryName':category,'sortOrder':index},second_token)
    for name,price,art,spec,description in items:
        product_code='SY-'+art
        if any(p['code']==product_code for p in second['products']):continue
        image='/images/products/'+art+'.svg'
        spu=second_products.get(product_code) or api('POST','/api/merchant/v1/products',{'categoryId':cat['id'],'spuCode':product_code,'productName':name,'description':description,'imageUrl':image},second_token)
        spu=api('PUT',f"/api/merchant/v1/products/{spu['id']}",{'categoryId':cat['id'],'productName':name,'description':description,'imageUrl':image,'status':'ACTIVE','version':spu['version']},second_token)
        sku=api('POST',f"/api/merchant/v1/products/{spu['id']}/skus",{'skuCode':product_code+'-1','skuName':spec,'specJson':json.dumps({'规格':spec},ensure_ascii=False),'basePriceCents':round(price*100),'allowStorePrice':True},second_token)
        sku=api('PUT',f"/api/merchant/v1/skus/{sku['id']}",{'skuName':spec,'specJson':sku.get('specJson'),'basePriceCents':round(price*100),'allowStorePrice':True,'status':'ACTIVE','version':sku['version']},second_token)
        api('PUT',f"/api/merchant/v1/stores/{second_store_id}/products/{sku['id']}",{'sellable':True,'storePriceCents':round(price*100)},second_token)
        api('POST',f"/api/merchant/v1/inventory/{sku['id']}/adjust",{'storeId':second_store_id,'quantityDelta':60,'idempotencyKey':'GARDEN-INITIAL-'+str(sku['id']),'reason':'花房公开展示初始库存'},second_token)
        second['products'].append({'code':product_code,'name':name,'category':category,'spuId':str(spu['id']),'skuIds':[str(sku['id'])],'price':price,'image':image});save()
(ROOT/'frontend/apps/consumer-web/.env.local').write_text('VITE_CONSUMER_TENANT_ID='+state['tenantId']+'\n',encoding='utf-8')
(ROOT/'frontend/apps/consumer-web/src/storefront.json').write_text(json.dumps({'tenantId':state['tenantId']},indent=2)+'\n',encoding='utf-8')
credentials.update({'tenantId':state['tenantId'],'storeId':storeId,'note':'初始本地账号，手机号为初始化占位账号；正式使用前修改联系信息及密码。','second':{**second_credentials,'tenantId':second['tenantId'],'storeId':second_store_id}})
CREDS.write_text(json.dumps(credentials,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'malls':[{'mall':state['mallName'],'tenantId':state['tenantId'],'storeId':storeId,'categories':len(catalog),'products':len(state['products']),'skus':sum(len(p['skuIds']) for p in state['products'])},{'mall':second['mallName'],'tenantId':second['tenantId'],'storeId':second_store_id,'categories':len(garden_catalog),'products':len(second['products']),'skus':sum(len(p['skuIds']) for p in second['products'])}],'credentialsFile':str(CREDS)},ensure_ascii=False))
