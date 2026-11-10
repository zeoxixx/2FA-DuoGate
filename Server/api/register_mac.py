#api/register_mac.py
#회원가입이 완료된 즉시 MAC 주소 id와 함께 전송
#OR MAC 주소 재등록시를 위한 api


import json
from flask import Blueprint, request, jsonify
from model.db import start_connection
from utils.aes_utils import aes_decrypt

register_mac_bp = Blueprint('register_mac', __name__)

@register_mac_bp.route('/register-mac', methods=['POST'])

def register_mac() :
    conn = None
    cursor = None

    try :
        raw = request.get_data(as_text=True)
        data = json.loads(raw)
        
        idApp = data.get("idApp")
        encrypted = data.get("payload")

        #유효성 검사
        if not all([idApp, encrypted]):
            return jsonify({"status" : "error", "message" : "ID 혹은 MAC 주소가 존재하지 않습니다."}), 400

        conn = start_connection()
        cursor = conn.cursor()

        cursor.execute("SELECT idUser FROM users WHERE idApp = %s", (idApp,))
        result = cursor.fetchone()

        if not result:
            return jsonify({"status" : "error", "message" : "존재하지 않는 사용자입니다."}), 404
        
        idUser = result[0]

        decrypted = aes_decrypt(encrypted, idUser)
        payload = json.loads(decrypted)
        macAddr = payload.get("macAddr")
        #ssid로 기기도 등록?

        cursor.execute("UPDATE users SET macAddr = %s WHERE idUser = %s",
                       (macAddr, idUser) )
        conn.commit()

        return jsonify({"status" : "success", "message" : "MAC 주소 등록이 완료되었습니다."}), 200

    except Exception as e :
        print(f"[ERROR] register-mac 예외: {repr(e)}") 
        if conn :
            conn.rollback()

        return jsonify({"status" : "error", "message" : str(e)}), 500

    finally :
        if cursor:
            cursor.close()
        if conn:
            conn.close()
