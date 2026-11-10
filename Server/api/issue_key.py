#api/issue_key.py
#app에서 회원가입 OR 키 재발급 버튼 -> 일회용 RSA 키페어 생성해서 공개키 서버로 전송함.
#서버는 aes 키 생성 후 app rsa 공개키로 암호화 후 전송

from flask import Blueprint, request, jsonify
from utils.create_key import create_key
from model.db import start_connection

issue_key_bp = Blueprint("issue_key", __name__)

@issue_key_bp.route("/issue-key", methods=["POST"])
def issue_key():
    conn = None
    cursor = None
    
    try:
        data = request.get_json()
        idApp = data.get("idApp")
        p_Key = data.get("publicKey")

        print(f"[DEBUG] 요청 수신 - idApp: {idApp}, 공개키 길이: {len(p_Key) if p_Key else '없음'}")


        #idUser가 없거나 공개키가 없는 경우 - > 에러 응답, http 상태 코드 = 400
        if not idApp or not p_Key:
            return jsonify({"status" : "error", "message":"유저ID 혹은 공개키가 없습니다"}), 400

        #전송받은 idApp으로 idUser DB에서 불러오기
        conn = start_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT idUser FROM users WHERE idApp = %s", (idApp,))
        result = cursor.fetchone()

        if not result:
            return jsonify({"status" : "error", "message":"해당 ID를 가진 사용자가 존재하지 않습니다"}),400

        idUser = result[0]

        print(f"[DEBUG] idUser 조회 성공: {idUser}")

        
        #utils 내부에 있는 키 생성, RSA 암호화 함수 실행
        aes_key = create_key(idUser, p_Key)

        if not aes_key :
            print("[ERROR] create_key() 결과가 None 또는 빈 문자열입니다.")
            return jsonify({"status": "error", "message": "AES 키 생성 실패"}), 500

        print(f"[DEBUG] 암호화된 AES 키 길이: {len(aes_key)}")

        return jsonify({"status" : "success", "aesKey" : aes_key})

    #예외 발생 -> 에러 응답, 서버 내부 오류 = 500
    except Exception as e :
        print(f"[ERROR] create_key 예외 발생: {str(e)}")
        return jsonify({"status" : "error", "message" : str(e)}), 500

    finally:
        if cursor: cursor.close()
        if conn: conn.close()

