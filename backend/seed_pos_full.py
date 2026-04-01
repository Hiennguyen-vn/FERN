#!/usr/bin/env python3
"""
Seed dữ liệu Product mới cho POS testing.
Tạo hoàn toàn mới, không phụ thuộc data cũ.
Bao gồm: Categories, Ingredients (UoM), Products, Recipes, Tax, Price, Availability
"""
import urllib.request
import urllib.error
import json
import ssl
from datetime import datetime

BASE = "http://localhost:8080"
IAM  = "http://localhost:8081"
TODAY = datetime.now().strftime("%Y-%m-%d")

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def req(method, url, data=None, token=None, idem_key=None):
    headers = {'Content-Type': 'application/json'}
    if token:      headers['Authorization'] = f'Bearer {token}'
    if idem_key:   headers['Idempotency-Key'] = idem_key
    body = json.dumps(data).encode() if data else b''
    r = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, context=ctx) as resp:
            txt = resp.read().decode()
            return json.loads(txt) if txt.strip() else {}
    except urllib.error.HTTPError as e:
        body_txt = e.read().decode()
        if e.code == 409:
            return {'_conflict': True}
        print(f"  ⚠ {method} {url} → HTTP {e.code}: {body_txt[:120]}")
        return None

def login(username, password):
    r = req('POST', f'{IAM}/auth/login', {'username': username, 'password': password})
    return r.get('accessToken') if r else None

def get_all(path, token):
    r = req('GET', f'{BASE}{path}?page=0&size=50', token=token)
    if r and 'content' in r:
        return r['content']
    return []

# ─── Auth ────────────────────────────────────────────────────────────────────
print("🔐 Đăng nhập bootstrap-admin...")
T = login('bootstrap-admin', 'Admin123!')
if not T:
    print("❌ Login failed!"); exit(1)
print("✅ Token acquired.")

# ─── Lấy Outlets ─────────────────────────────────────────────────────────────
outlets = get_all('/outlets', T)
outlet_ids = [o['id'] for o in outlets if o.get('code','').startswith('DEMO-')]
print(f"📍 Found {len(outlet_ids)} DEMO outlets: {outlet_ids}")
if not outlet_ids:
    outlet_ids = [o['id'] for o in outlets[:2]]
    print(f"   (dùng fallback outlets: {outlet_ids})")

# ─── Units of Measure ────────────────────────────────────────────────────────
print("\n📐 Tạo Units of Measure...")
uoms = [
    {'code': 'G',    'name': 'Gram',        'symbol': 'g'},
    {'code': 'ML',   'name': 'Millilitre',  'symbol': 'ml'},
    {'code': 'CUP',  'name': 'Cup',         'symbol': 'cup'},
    {'code': 'PHẦN', 'name': 'Phần',        'symbol': 'phần'},
    {'code': 'CÁI',  'name': 'Cái',         'symbol': 'cái'},
]
for u in uoms:
    r = req('POST', f'{BASE}/units-of-measure', u, T)
    if r and '_conflict' not in r:
        print(f"  ✅ UoM: {u['code']}")
    else:
        print(f"  ↩ UoM already exists: {u['code']}")

# ─── Ingredient Categories ────────────────────────────────────────────────────
print("\n📦 Tạo Ingredient Categories...")
ing_cats = [
    {'code': 'COFFEE-MAT', 'name': 'Nguyên liệu cà phê', 'description': 'Cà phê hạt, bột', 'active': True},
    {'code': 'DAIRY-MAT',  'name': 'Sữa & kem',           'description': 'Sữa tươi, kem tươi, sữa đặc', 'active': True},
    {'code': 'SYRUP-MAT',  'name': 'Syrup & đường',        'description': 'Đường, syrup các loại', 'active': True},
    {'code': 'TEA-MAT',    'name': 'Trà & thảo mộc',       'description': 'Trà đào, trà sen, trà xanh', 'active': True},
    {'code': 'FOOD-MAT',   'name': 'Nguyên liệu thực phẩm','description': 'Bánh, thức ăn', 'active': True},
]
for c in ing_cats:
    r = req('POST', f'{BASE}/ingredient-categories', c, T)

# ─── Ingredients ─────────────────────────────────────────────────────────────
print("\n🌿 Tạo Ingredients...")
ingredients_def = [
    {'code': 'CF-ARABICA',   'name': 'Cà phê Arabica',       'categoryCode': 'COFFEE-MAT', 'baseUomCode': 'G',    'status': 'ACTIVE'},
    {'code': 'CF-ROBUSTA',   'name': 'Cà phê Robusta',        'categoryCode': 'COFFEE-MAT', 'baseUomCode': 'G',    'status': 'ACTIVE'},
    {'code': 'MILK-FRESH',   'name': 'Sữa tươi nguyên kem',   'categoryCode': 'DAIRY-MAT',  'baseUomCode': 'ML',   'status': 'ACTIVE'},
    {'code': 'MILK-COND',    'name': 'Sữa đặc có đường',      'categoryCode': 'DAIRY-MAT',  'baseUomCode': 'ML',   'status': 'ACTIVE'},
    {'code': 'CREAM-FRESH',  'name': 'Kem tươi',               'categoryCode': 'DAIRY-MAT',  'baseUomCode': 'ML',   'status': 'ACTIVE'},
    {'code': 'SUGAR-WHITE',  'name': 'Đường trắng',            'categoryCode': 'SYRUP-MAT',  'baseUomCode': 'G',    'status': 'ACTIVE'},
    {'code': 'SYRUP-PEACH',  'name': 'Syrup đào',              'categoryCode': 'SYRUP-MAT',  'baseUomCode': 'ML',   'status': 'ACTIVE'},
    {'code': 'SYRUP-LYCHEE', 'name': 'Syrup vải',              'categoryCode': 'SYRUP-MAT',  'baseUomCode': 'ML',   'status': 'ACTIVE'},
    {'code': 'TEA-PEACH',    'name': 'Trà đào sấy khô',        'categoryCode': 'TEA-MAT',    'baseUomCode': 'G',    'status': 'ACTIVE'},
    {'code': 'TEA-GREEN',    'name': 'Trà xanh matcha',        'categoryCode': 'TEA-MAT',    'baseUomCode': 'G',    'status': 'ACTIVE'},
    {'code': 'TEA-OOLONG',   'name': 'Trà Oolong',             'categoryCode': 'TEA-MAT',    'baseUomCode': 'G',    'status': 'ACTIVE'},
    {'code': 'BREAD-STICK',  'name': 'Bánh mì que',            'categoryCode': 'FOOD-MAT',   'baseUomCode': 'CÁI',  'status': 'ACTIVE'},
    {'code': 'CROISSANT',    'name': 'Bánh Croissant',         'categoryCode': 'FOOD-MAT',   'baseUomCode': 'CÁI',  'status': 'ACTIVE'},
]
ING = {}
for i in ingredients_def:
    r = req('POST', f'{BASE}/ingredients', i, T)
    if r and '_conflict' not in r and 'id' in r:
        ING[i['code']] = r['id']
        print(f"  ✅ Ingredient: {i['name']} (ID {r['id']})")
    elif r and '_conflict' in r:
        # Resolve from DB via API
        print(f"  ↩ Ingredient exists: {i['code']}")

# ─── Product Categories ────────────────────────────────────────────────────────
print("\n🗂 Tạo Product Categories...")
prod_cats = [
    {'code': 'COFFEE-HOT',  'name': 'Cà Phê Nóng',  'description': 'Các loại cà phê nóng',   'active': True},
    {'code': 'COFFEE-ICE',  'name': 'Cà Phê Đá',    'description': 'Các loại cà phê đá',     'active': True},
    {'code': 'MILK-DRINK',  'name': 'Trà Sữa',       'description': 'Trà sữa & bubble tea',  'active': True},
    {'code': 'FRUIT-TEA',   'name': 'Trà Trái Cây',  'description': 'Trà trái cây tươi mát', 'active': True},
    {'code': 'FOOD-SNACK',  'name': 'Đồ Ăn Nhẹ',    'description': 'Bánh ngọt & đồ ăn nhẹ','active': True},
]
for c in prod_cats:
    r = req('POST', f'{BASE}/product-categories', c, T)

# ─── Products ─────────────────────────────────────────────────────────────────
print("\n☕ Tạo Products...")
products_def = [
    # Cà phê nóng
    {'code': 'CF-DEN-NONG',  'name': 'Cà Phê Đen Nóng',   'categoryCode': 'COFFEE-HOT', 'price': 29000,  'tax': 8.0,
     'desc': 'Cà phê đen truyền thống pha phin, đậm đà',
     'recipe_code': 'RCP-CF-DEN-NONG', 'recipe_desc': 'Phin cà phê đen nóng chuẩn vị',
     'ingredients': [
         {'code': 'CF-ROBUSTA', 'uom': 'G',  'qty': 20.0},
         {'code': 'SUGAR-WHITE','uom': 'G',  'qty': 5.0},
     ]},
    {'code': 'CF-SUA-NONG',  'name': 'Cà Phê Sữa Nóng',   'categoryCode': 'COFFEE-HOT', 'price': 35000, 'tax': 8.0,
     'desc': 'Cà phê phin kết hợp sữa đặc ngọt ngào',
     'recipe_code': 'RCP-CF-SUA-NONG', 'recipe_desc': 'Phin cà phê sữa nóng',
     'ingredients': [
         {'code': 'CF-ROBUSTA', 'uom': 'G',  'qty': 18.0},
         {'code': 'MILK-COND',  'uom': 'ML', 'qty': 30.0},
     ]},
    # Cà phê đá
    {'code': 'CF-DEN-DA',    'name': 'Cà Phê Đen Đá',     'categoryCode': 'COFFEE-ICE', 'price': 32000, 'tax': 8.0,
     'desc': 'Cà phê đen pha phin + đá viên',
     'recipe_code': 'RCP-CF-DEN-DA', 'recipe_desc': 'Phin cà phê đen đá',
     'ingredients': [
         {'code': 'CF-ROBUSTA', 'uom': 'G',  'qty': 22.0},
         {'code': 'SUGAR-WHITE','uom': 'G',  'qty': 5.0},
     ]},
    {'code': 'CF-SUA-DA',    'name': 'Cà Phê Sữa Đá',     'categoryCode': 'COFFEE-ICE', 'price': 38000, 'tax': 8.0,
     'desc': 'Cà phê sữa đá truyền thống Việt Nam',
     'recipe_code': 'RCP-CF-SUA-DA', 'recipe_desc': 'Phin cà phê sữa đá',
     'ingredients': [
         {'code': 'CF-ROBUSTA', 'uom': 'G',  'qty': 20.0},
         {'code': 'MILK-COND',  'uom': 'ML', 'qty': 35.0},
     ]},
    {'code': 'LATTE-ICE',    'name': 'Latte Đá',           'categoryCode': 'COFFEE-ICE', 'price': 55000, 'tax': 8.0,
     'desc': 'Espresso Arabica kết hợp sữa tươi và đá',
     'recipe_code': 'RCP-LATTE-ICE', 'recipe_desc': 'Iced Latte chuẩn barista',
     'ingredients': [
         {'code': 'CF-ARABICA', 'uom': 'G',  'qty': 18.0},
         {'code': 'MILK-FRESH', 'uom': 'ML', 'qty': 200.0},
     ]},
    {'code': 'AMERICANO-ICE','name': 'Americano Đá',       'categoryCode': 'COFFEE-ICE', 'price': 49000, 'tax': 8.0,
     'desc': 'Espresso pha loãng với nước + đá viên',
     'recipe_code': 'RCP-AMERICANO-ICE', 'recipe_desc': 'Iced Americano',
     'ingredients': [
         {'code': 'CF-ARABICA', 'uom': 'G',  'qty': 20.0},
     ]},
    {'code': 'CAPPU-ICE',    'name': 'Cappuccino Đá',      'categoryCode': 'COFFEE-ICE', 'price': 58000, 'tax': 8.0,
     'desc': 'Cappuccino lạnh với lớp foam béo ngậy',
     'recipe_code': 'RCP-CAPPU-ICE', 'recipe_desc': 'Iced Cappuccino',
     'ingredients': [
         {'code': 'CF-ARABICA', 'uom': 'G',  'qty': 18.0},
         {'code': 'MILK-FRESH', 'uom': 'ML', 'qty': 150.0},
         {'code': 'CREAM-FRESH','uom': 'ML', 'qty': 30.0},
     ]},
    # Trà sữa
    {'code': 'MATCHA-LATTE', 'name': 'Matcha Latte Đá',   'categoryCode': 'MILK-DRINK', 'price': 55000, 'tax': 8.0,
     'desc': 'Matcha xanh nguyên chất kết hợp sữa tươi',
     'recipe_code': 'RCP-MATCHA-LATTE', 'recipe_desc': 'Matcha Latte đá',
     'ingredients': [
         {'code': 'TEA-GREEN',  'uom': 'G',  'qty': 8.0},
         {'code': 'MILK-FRESH', 'uom': 'ML', 'qty': 200.0},
         {'code': 'SUGAR-WHITE','uom': 'G',  'qty': 15.0},
     ]},
    {'code': 'OOLONG-MILK',  'name': 'Trà Sữa Oolong',    'categoryCode': 'MILK-DRINK', 'price': 49000, 'tax': 8.0,
     'desc': 'Trà Oolong thơm ngậy với sữa tươi béo',
     'recipe_code': 'RCP-OOLONG-MILK', 'recipe_desc': 'Trà sữa Oolong',
     'ingredients': [
         {'code': 'TEA-OOLONG', 'uom': 'G',  'qty': 6.0},
         {'code': 'MILK-FRESH', 'uom': 'ML', 'qty': 180.0},
         {'code': 'SUGAR-WHITE','uom': 'G',  'qty': 15.0},
     ]},
    # Trà trái cây
    {'code': 'TRA-DAO-CS',   'name': 'Trà Đào Cam Sả',    'categoryCode': 'FRUIT-TEA',  'price': 45000, 'tax': 8.0,
     'desc': 'Trà đào tươi mát kết hợp cam sả',
     'recipe_code': 'RCP-TRA-DAO', 'recipe_desc': 'Trà đào cam sả',
     'ingredients': [
         {'code': 'TEA-PEACH',   'uom': 'G',  'qty': 5.0},
         {'code': 'SYRUP-PEACH', 'uom': 'ML', 'qty': 30.0},
         {'code': 'SUGAR-WHITE', 'uom': 'G',  'qty': 10.0},
     ]},
    {'code': 'TRA-VAI',      'name': 'Trà Vải Lychee',    'categoryCode': 'FRUIT-TEA',  'price': 45000, 'tax': 8.0,
     'desc': 'Trà vải thơm mát, ngọt tự nhiên',
     'recipe_code': 'RCP-TRA-VAI', 'recipe_desc': 'Trà vải lychee',
     'ingredients': [
         {'code': 'TEA-OOLONG',   'uom': 'G',  'qty': 5.0},
         {'code': 'SYRUP-LYCHEE', 'uom': 'ML', 'qty': 30.0},
         {'code': 'SUGAR-WHITE',  'uom': 'G',  'qty': 10.0},
     ]},
    # Đồ ăn nhẹ
    {'code': 'BANH-MI-QUE',  'name': 'Bánh Mì Que',       'categoryCode': 'FOOD-SNACK', 'price': 15000, 'tax': 8.0,
     'desc': 'Bánh mì que giòn rụm kiểu Hải Phòng',
     'recipe_code': 'RCP-BANH-MI', 'recipe_desc': 'Bánh mì que',
     'ingredients': [
         {'code': 'BREAD-STICK', 'uom': 'CÁI', 'qty': 1.0},
     ]},
    {'code': 'CROISSANT-BTR','name': 'Croissant Bơ',      'categoryCode': 'FOOD-SNACK', 'price': 35000, 'tax': 8.0,
     'desc': 'Bánh croissant bơ Pháp giòn nhiều lớp',
     'recipe_code': 'RCP-CROISSANT', 'recipe_desc': 'Croissant bơ',
     'ingredients': [
         {'code': 'CROISSANT', 'uom': 'CÁI', 'qty': 1.0},
     ]},
]

PROD_IDS = {}
for p in products_def:
    payload = {
        'code':         p['code'],
        'name':         p['name'],
        'categoryCode': p['categoryCode'],
        'status':       'ACTIVE',
        'description':  p['desc'],
    }
    r = req('POST', f'{BASE}/products', payload, T)
    if not r:
        continue
    if '_conflict' in r:
        print(f"  ↩ Product exists: {p['code']}")
        continue
    pid = r.get('id')
    if not pid:
        continue
    PROD_IDS[p['code']] = pid
    print(f"  ✅ Product: {p['name']} (ID {pid})")

    # Tax rate
    req('POST', f'{BASE}/tax-rates', {'productId': pid, 'taxPercent': p['tax'], 'effectiveFrom': TODAY}, T)

    # Price (GLOBAL)
    req('POST', f'{BASE}/product-prices', {
        'productId': pid, 'scopeType': 'GLOBAL', 'priceType': 'RETAIL',
        'currencyCode': 'VND', 'priceValue': float(p['price']), 'effectiveFrom': TODAY
    }, T)

    # Availability for all DEMO outlets
    for oid in outlet_ids:
        r2 = urllib.request.Request(
            f'{BASE}/product-availability',
            data=json.dumps({'productId': pid, 'outletId': oid, 'available': True}).encode(),
            headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {T}'},
            method='PUT'
        )
        try:
            with urllib.request.urlopen(r2, context=ctx): pass
        except: pass

    # Recipe + Version
    r_rcp = req('POST', f'{BASE}/recipes', {
        'productId':   pid,
        'recipeCode':  p['recipe_code'],
        'description': p['recipe_desc'],
    }, T)
    if r_rcp and '_conflict' not in r_rcp and r_rcp.get('id'):
        rcp_id = r_rcp['id']
        ing_list = []
        for idx, ing in enumerate(p.get('ingredients', []), 1):
            ing_id = ING.get(ing['code'])
            if ing_id:
                ing_list.append({'ingredientId': ing_id, 'uomCode': ing['uom'], 'qty': ing['qty'], 'sortOrder': idx})
        if ing_list:
            req('POST', f'{BASE}/recipe-versions', {
                'recipeId': rcp_id, 'versionNo': 'v1', 'yieldQty': 1.0,
                'yieldUomCode': 'CUP', 'status': 'ACTIVE', 'effectiveFrom': TODAY,
                'ingredients': ing_list
            }, T)
            print(f"     📋 Recipe v1 với {len(ing_list)} nguyên liệu")

print("\n" + "="*60)
print("✅ SEED COMPLETE!")
print("="*60)
print(f"📦 Products tạo mới: {len(PROD_IDS)}")
print(f"📍 Phủ outlets: {outlet_ids}")
print("\n🎯 MENU POS:")
for p in products_def:
    if p['code'] in PROD_IDS:
        print(f"   {p['name']:25s} {p['price']:>8,.0f} VNĐ")
print("\n💡 Đăng xuất & đăng nhập lại để nhận token mới!")
