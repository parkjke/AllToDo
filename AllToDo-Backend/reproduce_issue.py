import urllib.request
import urllib.parse
import json
import uuid

BASE_URL = "http://localhost:8000"
PASSWORD = "pw3355"

def post_json(url, data):
    req = urllib.request.Request(
        url, 
        data=json.dumps(data).encode('utf-8'), 
        headers={'Content-Type': 'application/json'}
    )
    with urllib.request.urlopen(req) as f:
        return json.loads(f.read().decode('utf-8'))

def get_json(url):
    with urllib.request.urlopen(url) as f:
        return json.loads(f.read().decode('utf-8'))

def reproduce():
    # 1. Create User
    user_uuid = str(uuid.uuid4())
    print(f"Creating user with UUID: {user_uuid}")
    try:
        resp = post_json(f"{BASE_URL}/check-user", {
            "uuid": user_uuid,
            "latitude": 37.5,
            "longitude": 127.0
        })
        print("Create User Resp:", resp)
    except Exception as e:
        print("Failed to create user:", e)
        return

    # 2. Update Info
    print("Updating User Info...")
    info_data = {
        "user_uuid": user_uuid,
        "name": "Test User",
        "password": "password123",
        "phone_number": "010-1234-5678",
        "age": 30,
        "address": "Seoul",
        "address_lat": 37.5,
        "address_long": 127.0,
        "work_address": "Gangnam",
        "work_lat": 37.4,
        "work_long": 127.1
    }
    try:
        resp = post_json(f"{BASE_URL}/update-info", info_data)
        print("Update Info Resp:", resp)
    except Exception as e:
        print("Failed to update info:", e)
        return

    # 3. Verify via Dev API
    print("Verifying via Dev API...")
    try:
        resp = get_json(f"{BASE_URL}/dev/tables/user_info?password={PASSWORD}")
        rows = resp
        
        found = False
        for row in rows:
            if row['user_uuid'] == user_uuid:
                print("SUCCESS: Found row in user_info table.")
                print("Row data:", row)
                found = True
                break
        
        if not found:
            print("FAILURE: Row NOT found in user_info table.")
            print("Total rows in user_info:", len(rows))
    except Exception as e:
        print("Failed to verify:", e)

if __name__ == "__main__":
    reproduce()
