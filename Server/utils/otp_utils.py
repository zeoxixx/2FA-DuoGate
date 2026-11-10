#utils/otp_utils
#TOTP 알고리즘을 활용하여 표준 라이브러리로 구현한 코드

import os
import time
import hmac
import base64
import struct
import hashlib
from datetime import datetime


# 20바이트 랜덤 키 생성 후 base32 인코딩
def generate_otp_secret(length=20) :
    random_bytes = os.urandom(length)
    return base64.b32encode(random_bytes).decode('utf-8') #byte->base32로

# 시간 기반 OTP 생성 함수
def generate_totp(totp_secret, for_time = None, digits=6, interval=30, algo=hashlib.sha256) :
    # 전처리
    if for_time is None:
        for_time = time.time()
    elif isinstance(for_time, datetime): # 시간 받아서 쓸 경우 자료형 변환
        for_time = for_time.timestamp()
        
    counter = int(for_time) // interval #현재 시간 기준으로 카운터 값 계산
    key = base64.b32decode(totp_secret.upper()) #base32를 대문자로 맞춘 후 byte로
    counter_bytes = struct.pack(">Q", counter) #TOTP표준으로 카운터를 big-endian, 8바이트로

    # OTP(key, counter) = Truncate(HMAC(key, counter)) mod 10^digits
    
    #HMAC SHA-256방식의 알고리즘으로.
    #HMAC(k, counter) = H(K xor Opad + H(K xor Ipad + counter)
    hmac_otp = hmac.new(key, counter_bytes, algo).digest()

    #truncation - 동적으로 string 잘라내기
    #20바이트에서 6자리 정수만 뽑아내는 과정

    #hmac_otp의 마지막 바이트를 0x0F(00001111)와 AND 연산 -> 하위 4비트 추출
    offset = hmac_otp[-1] & 0x0F #얘가 OTP 뽑아낼 숫자 영역 시작점이 됨.
    
    truncated_otp = hmac_otp[offset:offset+4] #offset 기준으로 4바이트 잘라내기

    #잘라낸 4바이트를 부호 없는 정수로 >I(big-endian),
    #0x7FFFFFFF로 부호 비트까지 제거 -> 양수로
    int_trunc = struct.unpack(">I", truncated_otp)[0] & 0x7FFFFFFF

    #6자리로 만들기    
    otp = int_trunc % (10 ** digits) # int_trunc를 10^6으로 나눈 나머지, otp = int_trunc mod 10^6
    return str(otp).zfill(digits)

#OTP가 유효한지 검증하는 함수, 30초로 설정
def verify_totp(input_otp, secret, digits=6, interval=30, window=1, algo=hashlib.sha256) :
    current_counter = int(time.time()) // interval # 현재 시간을 초 단위로 가져와서 시간 카운터 구하기
    key = base64.b32decode(secret.upper()) #otp 키 가져와서 decode, 대문자로 통일

    for offset in range(-window, window+1) :
        counter = current_counter + offset #현재 시간에서 +- 1스텝의 시간대를 허용.
        counter_bytes = struct.pack(">Q", counter)

        # 현재 시간 카운터로 다시 OTP 생성 후 비교 & 생성 과정
        
        hmac_otp = hmac.new(key, counter_bytes, algo).digest()
        dynamic_offset = hmac_otp[-1] & 0x0F

        truncated = hmac_otp[dynamic_offset : dynamic_offset + 4]

        int_trunc = struct.unpack(">I", truncated)[0] & 0x7FFFFFFF

        otp = str(int_trunc % (10 ** digits)).zfill(digits)

        if otp == input_otp :
            return True
    return False
