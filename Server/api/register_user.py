#api/register_user.py
#앱에서 회원가입 요청 시에 서버가 User T의 정보 등록.
#앱에서부터 appid, password, 이름, macAddr 받음

from flask import Blueprint, request, jsonify
from model.db import start_connection
from utils.rsa_utils import rsa_decrypt

register_user_bp = Blueprint("register_user", __name__)


#app 사용 id 중복 확인 -> 중복확인이 되어야 회원가입 버튼이 활성화 될 것.
@register_user_bp.route("/check-id", methods=["POST"])
def check_id() :
    conn = None
    cursor = None

    try :
        data = request.get_json()
        idApp = data.get("idApp")

        if not idApp :
            return jsonify({"status" : "error", "message" : "ID를 입력하세요."}), 400

        conn = start_connection()
        cursor = conn.cursor()

        cursor.execute("SELECT COUNT(*) FROM users WHERE idApp = %s", (idApp,))
        exists = cursor.fetchone()[0]

        if exists:
            return jsonify({"status" : "error", "message" :  "이미 존재하는 ID입니다."}), 400
        else :
            return jsonify({"status" : "success", "message" : "사용 가능한 ID입니다."}), 200

    except Exception as e:
        print(f"[ERROR] 예외 발생: {repr(e)}")  # ← 콘솔에 예외 출력
        return jsonify({"status": "error", "message": str(e)}), 500

    finally:
        try:
            if cursor:
                cursor.close()
            if conn:
                conn.close()
        except Exception as e:
            print(f"[WARNING] 자원 정리 중 에러 발생: {repr(e)}")
        

#회원가입 정보 - DB 저장 API
@register_user_bp.route("/register-user", methods=["POST"])
def register_user() :
    conn = None
    cursor = None

    try :
        data = request.get_json()
        idApp = data.get("idApp")
        
        #복호화 전 누락 확인
        if not all([idApp, data.get("name"), data.get("password")]):
            return jsonify({"status" : "error", "message" : "필수 항목 누락"}), 400

        conn = start_connection()
        cursor = conn.cursor()

        name = rsa_decrypt(data.get("name"))
        password = rsa_decrypt(data.get("password"))

        #복호화 후 누락 확인
        if not all([name, password]):
            return jsonify({"status": "error", "message": "복호화된 값이 비어있습니다"}), 400
        
        
        #id 중복 확인 한번 더
        cursor.execute("SELECT COUNT(*) FROM users WHERE idApp = %s", (idApp,))
        exists = cursor.fetchone()[0]

        if exists :
            return jsonify({"status" : "error", "message" : "이미 존재하는 ID입니다."}), 409

        cursor.execute(
            "INSERT INTO users (idApp, password, name, created, fail_locked) VALUES (%s, %s, %s, NOW(), %s)",
            (idApp, password, name, 0))
        conn.commit()

        return jsonify({"status" : "success", "message" : "회원 가입 및 키 등록이 완료되었습니다."})


    except Exception as e:
        if conn :
            conn.rollback()
        return jsonify({"status" : "error", "message" : str(e)}), 500

    finally:
        cursor.close()
        conn.close()
