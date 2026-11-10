#api/register_pi.py
#서버-라즈베리파이 간 통신 및 컨트롤을 위한 파일

import json
from flask import Blueprint, request, jsonify
import uuid, time
from model.db import start_connection
from utils.aes_utils import aes_encrypt_pi, aes_decrypt_pi

register_pi_bp = Blueprint("register-pi", __name__)

@register_pi_bp.route("/register-pi", methods=["POST"])
def register_pi():
    raw = request.get_data(as_text=True)
    print("[DEBUG] 받은 원문 데이터:", raw)

    try:
        data = json.loads(raw)
        print("[DEBUG] 파싱된 JSON:", data)
    except Exception as e:
        print("[ERROR] JSON 파싱 실패:", e)
        return jsonify({"status": "error", "message": f"JSON 파싱 오류 {str(e)}"}), 400

    ssid = data.get("ssid")
    encrypted = data.get("payload")
    print("[DEBUG] ssid:", ssid)
    print("[DEBUG] payload:", encrypted)

    if not ssid or not encrypted:
        print("[ERROR] ssid 또는 payload 누락")
        return jsonify({"status": "error", "message": "정보 누락"}), 400

    try:
        ip = aes_decrypt_pi(encrypted)
        print(f"[DEBUG] 복호화된 IP: {ip}")
    except Exception as e:
        print("[ERROR] 복호화 실패:", e)
        return jsonify({"status": "error", "message": f"AES 복호화 실패: {str(e)}"}), 400

    try:
        print("[DEBUG] DB 연결 시작")
        conn = start_connection()
        cursor = conn.cursor()
        print("[DEBUG] 커서 생성 완료")

        cursor.execute("SELECT idDevice FROM devices WHERE ssid = %s", (ssid,))
        exist = cursor.fetchone()
        
        # 기기 식별용 토큰 생성
        token = uuid.uuid4().hex
        encrypt_token = aes_encrypt_pi(token)
        print("token : ", token)

        if exist :
            cursor.execute("""
                UPDATE devices
                SET IPaddr = %s, LastOnline = NOW(), token = %s
                WHERE ssid = %s
                """, (ip, token, ssid))
            print("[PI] 기존 장비 갱신 완료")
            
        else :
            cursor.execute("""
                INSERT INTO devices (ssid, IPaddr, LastOnline, token)
                VALUES (%s, %s, NOW(), %s)
                """, (ssid, ip, token))
            print("[PI] 신규 장비 등록 완료")

        conn.commit()
        # 클라이언트에 암호화된 token 전송
        return jsonify({"token": encrypt_token})

    except Exception as e:
        print("[ERROR] DB 저장 실패:", e)
        return jsonify({"status": "error", "message": f"DB 오류: {str(e)}"}), 500
    finally:
        if cursor: cursor.close()
        if conn: conn.close()
