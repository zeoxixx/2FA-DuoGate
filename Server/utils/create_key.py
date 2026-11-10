#utils/create_key.py
#AES키를 생성 후 RSA 암호화까지 해서 DB 저장하는 파일

import base64
from Crypto.Random import get_random_bytes
from datetime import datetime, timedelta
from config.settings import AES_KEY, PUBLIC_KEY_PATH
from model.db import start_connection
from utils.rsa_utils import rsa_encrypt
from utils.otp_utils import generate_otp_secret
from Crypto.PublicKey import RSA

#issue-key에서 사용자 id, aes키 보호를 위한 사용자의 rsa 공개키를 받음
def create_key(idUser : int, publicKey : str) :
    print(f"현재 AES_KEY 설정값: {AES_KEY}") 
    aes_byte = get_random_bytes(AES_KEY) #AES KEY 생성(바이트)

    pem_app_key = (
            "-----BEGIN PUBLIC KEY-----\n" +
            publicKey.strip() +
            "\n-----END PUBLIC KEY-----"
        )
    pub_k_app = RSA.import_key(pem_app_key.encode())
    print("앱 공개키 PEM import 성공")
    
    encrypt_app = rsa_encrypt(aes_byte, pub_k_app) #전달받은 앱 공개키로 암호화

    #서버 쪽 저장을 위한 RSA키 가져와서 암호화
    with open(PUBLIC_KEY_PATH, "rb") as f:
        pub_k_server = RSA.import_key(f.read())

    print(f"pub_k_server == pub_k_app: {pub_k_server.export_key() == pub_k_app.export_key()}")


    encrypt_server = rsa_encrypt(aes_byte, pub_k_server)

    #TOTP 방식을 위한 otp key 생성
    otp_key = generate_otp_secret()
    print(f"생성된 otp 키 : {otp_key}")
    
    conn = start_connection()
    cursor = conn.cursor()

    try:
        cursor.execute("SELECT COUNT(*) FROM `keys` WHERE idUser = %s", (idUser,))
        exists = cursor.fetchone()[0]

        #키 업데이트는 6개월 (180일) 주기
        next_update = (datetime.now() + timedelta(days=180)).strftime("%Y-%m-%d %H:%M:%S")

        if exists : # 키 존재 - 업데이트 필요할 때
            cursor.execute(
                "UPDATE `keys` SET `key` = %s, `totp_secret` = %s, updated = %s WHERE idUser = %s",
                (encrypt_server, otp_key, next_update, idUser)
            )

        else : # 새롭게 생성할 때
            cursor.execute(
                "INSERT INTO `keys` (idUser, `key`, `totp_secret`, created, updated) VALUES (%s, %s, %s, NOW(), %s)",
                (idUser, encrypt_server, otp_key, next_update)
                )

        conn.commit()

    except Exception as e:
        print(f"[ERROR] create_key 예외 발생: {str(e)}")
        conn.rollback() #쿼리 실행 중 오류 -> 작업 취소
        raise e

    finally :
        cursor.close()
        conn.close()

    return encrypt_app
    
