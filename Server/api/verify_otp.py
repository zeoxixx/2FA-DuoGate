#api/verify_otp.py
#파이 -> 서버로 입력된 otp 전송

from flask import Blueprint, request, jsonify
from model.db import start_connection
from utils.aes_utils import aes_decrypt_pi, aes_encrypt, aes_encrypt_pi
from utils.otp_utils import verify_totp, generate_totp
from datetime import datetime
import json

verify_otp_bp = Blueprint("verify_otp", __name__)


@verify_otp_bp.route("/verify-otp", methods=["POST"])
def verify_otp() :
    conn = None
    cursor = None

    try :
        raw = request.get_data(as_text=True)
        data = json.loads(raw)

        encrypted_input = data.get("input")
        print("[DEBUG] 받은 input 암호문:", encrypted_input)
        encrypted_firstauth = data.get("firstauth_token")
        pi_token = data.get("device_token")
        timestamp = data.get("timestamp")

        if not all([encrypted_input, encrypted_firstauth, pi_token, timestamp]) :
            return jsonify({"status" : "error", "message" : "필수 데이터 누락"}), 400

        otp_input = aes_decrypt_pi(encrypted_input)
        firstauth_token = aes_decrypt_pi(encrypted_firstauth)

        conn = start_connection()
        cursor = conn.cursor(dictionary= True)

        # 전송 받은 1차인증 토큰이 유효한지 검사
        cursor.execute("SELECT idFirstAuth, idUser FROM firstauth WHERE token = %s", (firstauth_token,))
        row = cursor.fetchone()

        if not row:
            return jsonify({"status" : "error", "message" : "유효하지 않은 토큰"}), 400

        idFirstAuth = row["idFirstAuth"]
        idUser = row["idUser"]
        
        #OTP 유효하지만 이미 성공 처리됐으면 중복 인증 거부
        cursor.execute("""
            SELECT COUNT(*) AS success_count
            FROM secondauth
            WHERE idFirstAuth = %s AND result = 1 """, (idFirstAuth,))
        success_check = cursor.fetchone()
        if success_check["success_count"] > 0 :
            return jsonify({"status" : "already_verified", "message" : "이미 인증된 요청입니다."}), 409

        #계정이 잠금 상태인지 확인
        cursor.execute("SELECT fail_locked FROM users WHERE idUser = %s", (idUser,))
        locked_row = cursor.fetchone()

        if not locked_row or locked_row["fail_locked"] == 1 :
            return jsonify({"status" : "fail", "message" : "계정이 잠금 상태입니다."}), 403

        #OTP 비밀 키 불러오기
        cursor.execute("SELECT totp_secret FROM `keys` WHERE idUser = %s", (idUser,))
        secret_row = cursor.fetchone()
        if not secret_row :
            return jsonify({"status" : "error", "message" : "OTP 키가 없습니다."}), 500
        totp_secret = secret_row["totp_secret"]
        
        #2차 인증을 위한 OTP 발급 받았는지 확인
        cursor.execute("SELECT idSecondAuth, time FROM secondauth WHERE idFirstAuth = %s AND result = 4 ORDER BY time DESC LIMIT 1", (idFirstAuth,))
        auth_row = cursor.fetchone()

        if not auth_row:
            return jsonify({"status" : "error", "message" : "2차인증 테이블 기록 없음"}), 404

        idSecondAuth = auth_row["idSecondAuth"]
        otp_time = auth_row["time"]

        #실패 횟수 계산
        cursor.execute("""
            SELECT COALESCE(MAX(fail_num), 0) AS last_fail
            FROM secondauth
            WHERE idFirstAuth = %s
        """, (idFirstAuth,))
        fail_row = cursor.fetchone()
        last_fail = fail_row["last_fail"]

        # OTP 검증
        server_otp = generate_totp(totp_secret, for_time = otp_time)
        print("[DEBUG] 서버가 생성한 OTP:", server_otp)
        print("[DEBUG] 클라이언트가 입력한 OTP:", otp_input)

        is_valid = verify_totp(otp_input, totp_secret)
        print("[DEBUG] verify_totp() 결과:", is_valid)

        if otp_input != server_otp or not is_valid :
            locked = last_fail +1 >= 3

            cursor.execute("""
                UPDATE secondauth
                SET result = 0, fail_num = %s, input = %s, auth_time = NOW(3)
                WHERE idSecondAuth = %s
                """, (last_fail + 1, otp_input, idSecondAuth))

            if locked :
                cursor.execute("UPDATE users SET fail_locked = 1 WHERE idUser = %s", (idUser,))
                conn.commit()
                return jsonify({"status" : "LOCKED", "message" : "OTP 불일치, 계정 잠김", "fail_num" : last_fail + 1}), 401
            conn.commit()
            return jsonify({"status" : "fail", "message" : "OTP 불일치", "fail_num" : last_fail + 1}), 403

        # 성공 시 기존 result = 4 레코드 업데이트
        cursor.execute("""
            UPDATE secondauth
            SET result = 1, input = %s, auth_time = NOW(3), fail_num = %s
            WHERE idSecondAuth = %s
        """, (otp_input, last_fail, idSecondAuth))
        conn.commit()

        encrypted_idSecondAuth = aes_encrypt_pi(str(idSecondAuth))
        print("[DEBUG] idSecondAuth to encrypt:", idSecondAuth)

        return jsonify({"status": "success", "idSecondAuth": encrypted_idSecondAuth})
    
    except Exception as e:
        print("[ERROR] verify_otp: " ,e)
        return jsonify({"status" : "error", "message" : str(e)}), 500

    finally:
        if cursor: cursor.close()
        if conn: conn.close()
 
@verify_otp_bp.route("/check-result", methods=["GET"])
def check_result() :
    idApp = request.args.get("idApp")
    if not idApp:
        return jsonify({"status" : "error", "message": "idApp 누락"}), 400

    conn = start_connection()
    cursor = conn.cursor(dictionary = True)
    try:
        cursor.execute("SELECT idUser FROM users WHERE idApp = %s", (idApp,))
        user_row = cursor.fetchone()
        if not user_row:
            return jsonify({"status" : "error", "message" : "존재하지 않는 id입니다."})

        idUser = user_row["idUser"]

        cursor.execute("""
            SELECT s.result, s.auth_time, s.fail_num
            FROM firstauth f
            JOIN secondauth s ON f.idFirstAuth = s.idFirstAuth
            WHERE f.idUser = %s
            ORDER BY s.time DESC
            LIMIT 1
            """, (idUser,))
        result = cursor.fetchone()

        if not result :
            print("PENDING")
            return jsonify({"status" : "pending"})

        from datetime import datetime, timedelta
        otp_time = result["auth_time"]
        if otp_time is None:
            return jsonify({"status": "pending"})
        
        if datetime.now() - otp_time > timedelta(seconds = 30) :
            return jsonify({"status" : "expired_otp"})

        if result["result"] == 4 :
            return jsonify({"status" : "pending"})

        elif result["result"] == 1 :
            return jsonify({"status" : "success"})

        elif result["fail_num"] is not None and result["fail_num"] >= 3:
            return jsonify({"status" : "LOCKED"})
        else:
            return jsonify({"status" : "fail", "fail_num" : result["fail_num"]})

    except Exception as e:
        print("[ERROR] check-result :", e)
        return jsonify({"status" : "error", "message" : str(e)}), 500

    finally :
        if cursor : cursor.close()
        if conn : conn.close()
