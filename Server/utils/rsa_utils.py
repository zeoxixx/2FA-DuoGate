#utils/rsa_utils.py
#RSA암호화와 복호화를 위한 파일

import base64
from Crypto.PublicKey import RSA
from Crypto.Cipher import PKCS1_v1_5
from config.settings import PRIVATE_KEY_PATH
from typing import Union

def rsa_encrypt(data:bytes, pub_key: Union[str, RSA.RsaKey]) -> str:
    try:
        print(f"[DEBUG] rsa_encrypt 호출 - 입력 데이터 길이: {len(data)}")
        cipher = PKCS1_v1_5.new(pub_key)
        encrypted = cipher.encrypt(data)
        return base64.b64encode(encrypted).decode()
    except Exception as e:
        raise ValueError(f"RSA 암호화 실패: {str(e)}")

def rsa_decrypt(encrypted: str) -> bytes:
    try:
        with open(PRIVATE_KEY_PATH, "rb") as f:
            p_key = RSA.import_key(f.read())

        cipher = PKCS1_v1_5.new(p_key)
        decoded_data = base64.b64decode(encrypted)
        print(f"[RSA] base64 decode OK, 길이: {len(decoded_data)} bytes")

        # 복호화 시 None 반환될 수 있으므로 체크
        decrypted = cipher.decrypt(decoded_data, None)
        if decrypted is None:
            raise ValueError("복호화 실패: 복호화된 값이 None입니다 (패딩 불일치 또는 잘못된 키)")

        return decrypted

    except Exception as e:
        import traceback
        traceback.print_exc()  # ← 터미널에 전체 스택 트레이스 출력
        raise ValueError(f"RSA 복호화 실패: {str(e)}")
