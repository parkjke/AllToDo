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

def verify_recovery():
    # 1. Create User with Nickname
    user_uuid = str(uuid.uuid4())
    nickname = f"user_{user_uuid[:8]}"
    print(f"Creating user with UUID: {user_uuid}, Nickname: {nickname}")
    
    try:
        resp = post_json(f"{BASE_URL}/check-user", {
            "uuid": user_uuid,
            "latitude": 37.5,
            "longitude": 127.0,
            "nickname": nickname
        })
        print("Create User Resp:", resp)
    except Exception as e:
        print("Failed to create user:", e)
        return

    # 2. Set Password (via update-info)
    print("Setting Password...")
    user_password = "my_secure_password"
    try:
        resp = post_json(f"{BASE_URL}/update-info", {
            "user_uuid": user_uuid,
            "password": user_password,
            "name": "Recovery Test User"
        })
        print("Update Info Resp:", resp)
    except Exception as e:
        print("Failed to set password:", e)
        return

    # 3. Recover UUID
    print("Attempting Recovery...")
    try:
        resp = post_json(f"{BASE_URL}/recover-uuid", {
            "nickname": nickname,
            "password": user_password
        })
        print("Recovery Resp:", resp)
        if resp['uuid'] == user_uuid:
            print("SUCCESS: UUID recovered correctly.")
        else:
            print("FAILURE: Recovered UUID does not match.")
    except Exception as e:
        print("Failed to recover:", e)

    # 4. Get User Info
    print("Getting User Info...")
    try:
        resp = get_json(f"{BASE_URL}/user-info?uuid={user_uuid}")
        print("User Info Resp:", resp)
        if resp['name'] == "Recovery Test User":
            print("SUCCESS: User info retrieved and decrypted.")
        else:
            print("FAILURE: User info name mismatch.")
            
        if resp['phone_number'] is None:
             print("SUCCESS: Empty field returned as None.")
        else:
             print("FAILURE: Empty field not None.")
             
    except Exception as e:
        print("Failed to get user info:", e)

if __name__ == "__main__":
    verify_recovery()
