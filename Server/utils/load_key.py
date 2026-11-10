#utils/load_key.py
#DB에 암호화된 AES 키 가져와서 복호화하는 함수 파일

import base64
from Crypto.PublicKey import RSA
from Crypto.Cipher import PKCS1_v1_5
from model.db import start_connection
from config.settings import DB_CONFIG, PRIVATE_KEY_PATH

def get_aes_key(idUser: int) -> bytes:

    #DB 연결 - 암호화 된 AES KEY 가져오기
    conn = start_connection()
    cursor = conn.cursor(dictionary=True)
    cursor.execute("SELECT `key` FROM `keys` WHERE idUser = %s", (idUser,))

    row = cursor.fetchone()
    cursor.close()
    conn.close()

    if not row :
        raise Exception(f"사용자 {idUser}의 AES 키가 존재하지 않습니다.")

    row_b64 = row["key"]
    encrypted_key = base64.b64decode(row_b64)

    #복호화 과정

    with open(PRIVATE_KEY_PATH, "rb") as f :
        p_key = RSA.import_key(f.read())

    #개인키 패딩
    cipher = PKCS1_v1_5.new(p_key)
    #최종 RSA 복호화
    result = cipher.decrypt(encrypted_key, None)

    return result

def load_pi_key() -> bytes:
    try:
        with open("./key/pi_aes.key", "r") as f:
            key = f.read().strip()
            return base64.b64decode(key)
    except Exception as e:
        raise Exception(f"[load_pi_key] 키 불러오기 실패 : {e}")
