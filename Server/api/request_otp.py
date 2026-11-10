#api/request_otp.py
#앱에서 출입하기 버튼 -> 1차인증 토큰, timestamp을 서버에 전송
#서버는 토큰 유효 확인 후 idApp->idUser 조회.
#idUser에 맞는 otp키로 otp 생성 후 전송
#otp 생성 후 result 값에 4 -> 인증 대기 중인 것. => 여전히 0은 실패, 1이 성공임.

from flask import Blueprint, request, jsonify
from datetime import datetime, timedelta
from model.db import start_connection
from utils.aes_utils import aes_decrypt, aes_encrypt
from utils.otp_utils import generate_totp
import base64
import json

request_otp_bp = Blueprint("request_otp", __name__)
@request_otp_bp.route("/request-otp", methods=["POST"])
def request_otp():
    try:
        data = request.get_json()
        idApp = data.get("idApp")
        encrypted_payload = data.get("payload")

        if not idApp or not encrypted_payload :
            return jsonify({"status": "fail", "message" : "필드 누락"}), 400

        
        conn = start_connection()
        cursor = conn.cursor(dictionary=True)
       # 트랜잭션 시작
        conn.start_transaction()

        cursor.execute("SELECT idUser, fail_locked FROM users WHERE idApp = %s", (idApp,))
        result = cursor.fetchone()

        if not result:
            return jsonify({"status": "error", "message": "유효하지 않은 사용자입니다."}), 404

        idUser = result["idUser"]
        fail_locked = int(result["fail_locked"])

        if fail_locked:
            return jsonify({"status": "LOCKED", "message": "계정 잠김, 관리자 문의"}), 403

        decrypted_payload = aes_decrypt(encrypted_payload, idUser)
        print("복호화 결과 원문:", decrypted_payload)

        payload = json.loads(decrypted_payload)

        token = payload.get("token")
        req_type = payload.get("request_type")
        req_time = payload.get("timestamp")

        cursor.execute("""
            SELECT expire_time, idFirstAuth FROM firstauth
            WHERE idUser = %s AND token = %s
            ORDER BY time DESC LIMIT 1
        """, (idUser, token))
        result = cursor.fetchone()

        if not result:
            return jsonify({"status": "fail", "message": "토큰 불일치"}), 400

        expire_time = result["expire_time"]
        idFirstAuth = result["idFirstAuth"]

        if datetime.now() > expire_time:
            return jsonify({"status": "expired_token", "message": "토큰 만료"}), 403

        cursor.execute("SELECT totp_secret FROM `keys` WHERE idUser = %s", (idUser,))
        result = cursor.fetchone()

        if not result:
            return jsonify({"status": "fail", "message": "TOTP 키가 존재하지 않습니다."}), 400
        totp_secret = result["totp_secret"]

        # 유틸 함수로 OTP 생성
        otp_time = datetime.now()
        otp = generate_totp(totp_secret, for_time = otp_time)
        print(f"{idUser}의 생성된 OTP: {otp}")

        # OTP 생성 후 인증 대기 상태 INSERT (기존 SELECT 생략하고 무조건 새로 생성)
        cursor.execute("""
        INSERT INTO secondauth (idFirstAuth, input, result, time, fail_num, auth_time)
        VALUES (%s, NULL, 4, %s, 0, NULL)
        """, (idFirstAuth, otp_time))
        
        conn.commit()
        encrypted_otp = aes_encrypt(otp, idUser)

        return jsonify({"status": "success", "otp": encrypted_otp, "timestamp": otp_time.isoformat()})

    except Exception as e:
        if conn:
            conn.rollback()
        print(f"[ERROR] request_otp 예외 발생: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


    finally:
        if 'cursor' in locals(): cursor.close()
        if 'conn' in locals(): conn.close()
