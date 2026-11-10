#utils/aes_utils.py
#AES 암호화, 복호화 함수 파일
#AES 암호화 GCM 방식으로 구현함

import json
import base64
from Crypto.Cipher import AES
from Crypto.Random import get_random_bytes
from utils.load_key import get_aes_key, load_pi_key


# 서버-앱 간의 암호화/복호화
def aes_encrypt(data: str, idUser : int) -> str:
    key = get_aes_key(idUser)
    print("[DEBUG] 서버 AES 키:", base64.b64encode(key).decode()[:20])
    return common_aes_encrypt(data, key)
    
def aes_decrypt(encrypted: str, idUser : int) -> str:
    key = get_aes_key(idUser)
    return common_aes_decrypt(encrypted, key)

# 암호화 동작
def common_aes_encrypt(data: str, key: bytes) -> str:
    nonce = get_random_bytes(12)
    #암호화 객체 생성 (AES키 초기화, 모드 설정, nonce 설정, Galois 연산준비)
    cipher = AES.new(key, AES.MODE_GCM, nonce=nonce)
    # 암호화 + 인증 태그 생성 (CTR 키스트림 생성, 암호화, Galois 연산)
    ciphertext, tag = cipher.encrypt_and_digest(data.encode())

    encrypted_package = {
        "nonce" : base64.b64encode(nonce).decode(),
        "tag" : base64.b64encode(tag).decode(),
        "ciphertext" : base64.b64encode(ciphertext).decode() }
    return base64.b64encode(json.dumps(encrypted_package).encode()).decode()

# 복호화 동작 - 암호화의 반대로
def common_aes_decrypt(encrypted: str, key: bytes) -> str:
    try:
        decoded_package = json.loads(base64.b64decode(encrypted).decode())
        print("[DEBUG] decoded 바이트:", str(decoded_package)[:100])
        nonce = base64.b64decode(decoded_package["nonce"])
        tag = base64.b64decode(decoded_package["tag"])
        ciphertext = base64.b64decode(decoded_package["ciphertext"])

        cipher = AES.new(key, AES.MODE_GCM, nonce=nonce)
        plaintext = cipher.decrypt_and_verify(ciphertext, tag)
        return plaintext.decode()

    except (ValueError, KeyError) as e:
        raise Exception(f"복호화 실패: {str(e)}")


def aes_encrypt_pi(data: str) -> str:
    key = load_pi_key()
    return common_aes_encrypt(data, key)
def aes_decrypt_pi(encrypted: str) -> str:
    key = load_pi_key()
    return common_aes_decrypt(encrypted, key)
