#api/verify_mac.py
#MAC 인증을 처리하는 파일

#app->server에서 보낸 데이터
# marAddr, request_type, timestamp

#server->app으로 보낼 데이터
# status, idUser, firstauth_token, token_expire, idFirstAuth
# 동시에
#server->pi로 보낼 데이터
# status, firstauth_token

#성공 = 1, 실패 = 0

import json
import uuid
from flask import Blueprint, request, jsonify
from utils.aes_utils import aes_encrypt, aes_decrypt, aes_encrypt_pi
from model.db import start_connection
from config.settings import TOKEN_EXPIRE_MINUTES
from datetime import datetime, timedelta
from dateutil.parser import parse

#Blueprint 객체 생성
verify_mac_bp = Blueprint("verify_mac", __name__)

@verify_mac_bp.route("/verify-mac", methods=["POST"])
def verify_mac():
    conn = None
    cursor = None

    try:
        raw = request.get_data(as_text=True)
        data = json.loads(raw)
        req_time_server = datetime.now()

        idApp = data.get("idApp")
        encrypted = data.get("payload")

        conn = start_connection()
        cursor = conn.cursor(dictionary=True)

        cursor.execute("SELECT idUser, macAddr FROM users WHERE idApp = %s", (idApp,))
        result = cursor.fetchone()
        if not result:
            return jsonify({"status": "error", "message": "유효하지 않은 사용자입니다."}), 404

        idUser = result["idUser"]
        reg_mac = result["macAddr"]

        decrypted_payload = aes_decrypt(encrypted, idUser)
        print("[DEBUG] 복호화 결과 원문:", decrypted_payload)
        payload = json.loads(decrypted_payload)

        mac = payload.get("macAddr")
        req_type = payload.get("request_type")
        req_time_iso = payload.get("timestamp")
        ssid = payload.get("ssid")
        print("[DEBUG] payload에서 받은 ssid:", ssid)

        try:
            req_time = parse(req_time_iso)
        except Exception as e:
            print("[ERROR] timestamp 파싱 실패:", e)
            req_time = datetime.now()

        if reg_mac == mac:
            first_auth_token = uuid.uuid4().hex
            expire_dt = datetime.now() + timedelta(minutes=TOKEN_EXPIRE_MINUTES)
            token_expire_str = expire_dt.isoformat(timespec='milliseconds')

            cursor.execute("""
                INSERT INTO firstAuth (idUser, macAddrFromApp, result, timeFromApp, time, req_time_server, token, expire_time)
                VALUES (%s, %s, %s, %s, NOW(3), %s, %s, %s)
            """, (idUser, mac, 1, req_time, req_time_server, first_auth_token, token_expire_str))

            idFirstAuth = cursor.lastrowid
            conn.commit()

            # 라즈베리 파이로 firstauth-token 전송
            cursor.execute("SELECT IPaddr FROM devices WHERE ssid = %s", (ssid,))
            pi_result = cursor.fetchone()
            print("[DEBUG] SELECT된 pi_result:", pi_result)
            
            if pi_result:
                pi_ip = pi_result["IPaddr"]
                pi_notify_url = f"http://192.168.137.229:5001/notify-firstauth"

                notify_payload = {
                    "status" : aes_encrypt_pi("success"),
                    "firstauth_token" : aes_encrypt_pi(first_auth_token)
                    }

                print("[DEBUG] Pi에 전송할 notify_payload:", notify_payload)
                
                try :
                    import requests
                    notify_res = requests.post(pi_notify_url, json=notify_payload, timeout=10)

                    if notify_res.status_code != 200:
                        raise Exception(f"Pi 응답 비정상: {notify_res.status_code}")
                    print(f"[DEBUG] Pi notified: {notify_res.status_code}, {notify_res.text}")
                except Exception as e:
                    print("[ERROR] 라즈베리파이 전송 실패:", e)
                    #return jsonify({"status" : "error", "message" : "Pi 전달 실패"})


            response = {
                "status": "success",
                "idUser": idUser,
                "token": first_auth_token,
                "expire_token": token_expire_str
            }

            encrypted_response = aes_encrypt(json.dumps(response), idUser)
            status_code = 200
            print("[DEBUG] 암호화된 응답 Base64:", encrypted_response)
            return encrypted_response, status_code

        else:
            # 사용자 MAC 매칭 실패 -> result = 0
            # FirstAuth 테이블에 실패 기록
            cursor.execute("""
                INSERT INTO firstAuth (idUser, macAddrFromApp, result, timeFromApp, time, req_time_server, token, expire_time)
                VALUES (%s, %s, %s, %s, NOW(3), %s, %s, %s)
            """, (None, mac, 0, req_time, datetime.now().isoformat(), req_time_server, None, None))

            return jsonify({"status": "fail"}), 400

        conn.commit()

    except Exception as e:
        if conn:
            conn.rollback()
        print("[ERROR] verify_mac:", e)
        return jsonify({"status": "error", "message": str(e)}), 500

    
    finally:
        if cursor: cursor.close()
        if conn: conn.close()
