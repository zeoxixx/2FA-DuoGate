#api/open_record.py
#파이 -> 서버로 도어락 오픈 기록 (디바이스 토큰, idSecondAuth, 시도횟수=count, result, 시간)

from flask import Blueprint, request, jsonify
from model.db import start_connection
from utils.aes_utils import aes_decrypt_pi
from datetime import datetime
import json
import base64
import traceback

open_record_bp = Blueprint("open_record", __name__)
@open_record_bp.route("/open-record", methods=["POST"])
def open_record() :
    conn = start_connection()
    cursor = conn.cursor(dictionary=True)
    try:
        data = request.get_json()
        encrypted_device = data.get("device_token")
        encrypted_idSecondAuth = data.get("idSecondAuth")
        encrypted_count = data.get("count")
        encrypted_result = data.get("result")
        encrypted_time = data.get("time")

        if not all([encrypted_device, encrypted_idSecondAuth, encrypted_count, encrypted_result, encrypted_time]) :
            return jsonify({"status" : "error", "message" : "필수 데이터 누락"}), 400

        device_token = aes_decrypt_pi(encrypted_device)
        idSecondAuth = int(aes_decrypt_pi(encrypted_idSecondAuth))
        count = int(aes_decrypt_pi(encrypted_count))
        result = int(aes_decrypt_pi(encrypted_result))
        open_time_str = aes_decrypt_pi(encrypted_time)

        try:
            open_time = datetime.fromisoformat(open_time_str)
        except Exception as e:
            print("[ERROR] 시간 파싱 실패: ", e)
            return jsonify({"status" : "error", "message" : "시간 형식 오류"}), 400


        #디바이스 토큰 유효한가
        cursor.execute("SELECT idDevice FROM devices WHERE token = %s", (device_token,))
        row = cursor.fetchone()
        if not row :
            return jsonify({"status" : "error", "message" : "기기 정보 없음"}), 404

        idDevice = row["idDevice"]
        
        cursor.execute("SELECT idFirstAuth, result FROM secondauth WHERE idSecondAuth = %s", (idSecondAuth,))
        auth_row = cursor.fetchone()

        if not auth_row :
            return jsonify({"status" : "error", "message" : "인증 기록 없음"}), 404

        if auth_row["result"] != 1 :
            return jsonify({"status" : "error", "message" : "인증 성공 기록이 아님"}), 409

        idFirstAuth = auth_row["idFirstAuth"]

        cursor.execute("""
            INSERT INTO openrecord (idDevice, idSecondAuth, time, n, isOpened)
            VALUES (%s, %s, %s, %s, %s) """, (idDevice, idSecondAuth, open_time, count, result))

        conn.commit()
        return jsonify({"status" : "success"})

    except Exception as e:
        print("[ERROR] open_record: ", e)
        traceback.print_exc()
        return jsonify({"status" : "error", "message" : str(e)}), 500

    finally:
        if cursor : cursor.close()
        if conn : conn.close()
            
