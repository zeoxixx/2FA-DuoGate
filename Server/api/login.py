#api/login.py
#login으로 DB 쿼리 비교를 위한 파일

from flask import Blueprint, request, jsonify
from model.db import start_connection
from utils.aes_utils import aes_encrypt, aes_decrypt

login_bp = Blueprint("login", __name__)
@login_bp.route("/login", methods = ["POST"])

def login():
    conn = None
    cursor = None

    try:
        data = request.get_json()
        idApp = data.get("idApp")
        encrypt_pw = data.get("password")

        if not all([idApp, encrypt_pw]):
            return jsonify({"status" : "error", "message" : "누락된 항목이 있습니다."}), 400

        conn = start_connection()
        cursor = conn.cursor()

        cursor.execute("SELECT idUser, password FROM users WHERE idApp = %s", (idApp,))
        result = cursor.fetchone()
        
        if not result :
            return jsonify({"status" : "error", "message" : "ID를 확인하세요."}), 400


        idUser, db_pw = result
        password = aes_decrypt(encrypt_pw, idUser)
        
        if db_pw == password :
            return jsonify({"status" : "success", "message" : "로그인에 성공하였습니다."}), 200

        else :
            return jsonify({"status" : "error", "message" : "비밀번호를 확인하세요."}), 400

    except Exception as e:
        import traceback
        traceback.print_exc()  # ← 터미널에 전체 스택 트레이스 출력
        return jsonify({"status": "error", "message": str(e)}), 500

    print("[DEBUG] 복호화된 password:", repr(password))
    print("[DEBUG] DB password:", repr(db_pw))
