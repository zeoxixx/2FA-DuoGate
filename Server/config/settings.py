#config/settings.py
#DB와 AES키 기본 세팅을 위한 파일

DB_CONFIG = {
    "host" : "localhost",
    "user" : "root",
    "password" : "DB의 비밀번호",
    "database" : "midas",
    }


AES_KEY = 32 #32byte

#세션 토큰 유효 시간 (분)
TOKEN_EXPIRE_MINUTES = 2

#RSA KEY 저장 경로
PRIVATE_KEY_PATH = "C:/Users/82108/OneDrive/바탕 화면/2FA_Server/key/private.pem"
PUBLIC_KEY_PATH  = "C:/Users/82108/OneDrive/바탕 화면/2FA_Server/key/public.pem"
