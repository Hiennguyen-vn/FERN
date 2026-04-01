import urllib.request
import urllib.error
import json
import ssl
from datetime import datetime

base_url = "http://localhost:8080"
iam_url = "http://localhost:8081"
username = "bootstrap-admin"
password = "Admin123!"

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def do_post(url, data=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8') if data else b"", headers=headers, method='POST')
    try:
        with urllib.request.urlopen(req, context=ctx) as r:
            return json.loads(r.read().decode()) if r.getcode() in [200, 201] else r.read().decode()
    except urllib.error.HTTPError as e:
        if e.code == 409: # Conflict - already exists
            return None
        print(f"Error {e.code} on {url}: {e.read().decode()}")
        return None

def do_put(url, data, token):
    headers = {'Content-Type': 'application/json', 'Authorization': f'Bearer {token}'}
    req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8'), headers=headers, method='PUT')
    try:
        with urllib.request.urlopen(req, context=ctx) as r:
            pass
    except urllib.error.HTTPError as e:
        pass

def get_outlets(token):
    req = urllib.request.Request(f"{base_url}/outlets?page=0&size=10", headers={'Authorization': f'Bearer {token}'})
    try:
        with urllib.request.urlopen(req, context=ctx) as r:
            res = json.loads(r.read().decode())
            return [o['id'] for o in res.get('content', [])]
    except:
        return []

# 1. Login
auth_res = do_post(f"{iam_url}/auth/login", {"username": username, "password": password})
if not auth_res or 'accessToken' not in auth_res:
    print("Login failed")
    exit(1)
token = auth_res['accessToken']

outlets = get_outlets(token)
business_date = datetime.now().strftime("%Y-%m-%d")

products = [
    {"code": "CPSD-ICE", "name": "Cà Phê Sữa Đá", "categoryCode": "COLD-DRINKS", "status": "ACTIVE", "desc": "Cà phê sữa đá truyền thống", "price": 35000},
    {"code": "BX-ICE", "name": "Bạc Xỉu", "categoryCode": "COLD-DRINKS", "status": "ACTIVE", "desc": "Bạc xỉu nhiều sữa", "price": 39000},
    {"code": "TDCS-ICE", "name": "Trà Đào Cam Sả", "categoryCode": "COLD-DRINKS", "status": "ACTIVE", "desc": "Trà đào kết hợp cam sả tươi mát", "price": 45000},
    {"code": "MC-LATTE", "name": "Matcha Latte", "categoryCode": "COLD-DRINKS", "status": "ACTIVE", "desc": "Latte trà xanh nguyên chất", "price": 50000},
    {"code": "BM-QUE", "name": "Bánh Mì Que Hải Phòng", "categoryCode": "HOT-DRINKS", "status": "ACTIVE", "desc": "Bánh mì que giòn rụm", "price": 15000}, # Use existing category instead of making new one to save time
]

for p in products:
    # Create product
    resp = do_post(f"{base_url}/products", {"code": p['code'], "name": p['name'], "categoryCode": p['categoryCode'], "status": p['status'], "description": p['desc']}, token)
    if not resp:
        print(f"Product {p['code']} already exists or error.")
        continue
    
    pid = resp['id']
    print(f"Created Product: {p['name']} (ID {pid})")
    
    # 2. Add Tax
    do_post(f"{base_url}/tax-rates", {"productId": pid, "taxPercent": 8.00, "effectiveFrom": business_date}, token)
    
    # 3. Add Price
    do_post(f"{base_url}/product-prices", {"productId": pid, "scopeType": "GLOBAL", "priceType": "RETAIL", "currencyCode": "VND", "priceValue": p['price'], "effectiveFrom": business_date}, token)
    
    # 4. Add Availability
    for o_id in outlets:
        do_put(f"{base_url}/product-availability", {"productId": pid, "outletId": o_id, "available": True}, token)

print("Product seeding complete for POS testing.")
