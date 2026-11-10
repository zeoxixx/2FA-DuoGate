import RPi.GPIO as GPIO
import sys
sys.path.append('/home/pi/RPi_I2C_LCD_driver')
import threading
import requests
import base64
import json
from time import sleep
from Crypto.Cipher import AES
from Crypto.Random import get_random_bytes
from datetime import datetime
import RPi_I2C_driver
from flask_thread import auth_ready, shared_state, start_flask

# Start Flask server thread
threading.Thread(target=start_flask, daemon=True).start()

AES_KEY = b'!\xca\xd6h\xb3\xff\x16\x19a=H4\xf1\xd2p\xa0\xd0\x14+\xcci\x0b\xea\xb3\x113b\xac\x8f\x074Y'
SERVER_URL = "http://192.168.137.23:5000"

lcd = RPi_I2C_driver.lcd()
lcd.lcd_clear()

KEYPAD = [["1", "2", "3", "A"], ["4", "5", "6", "B"], ["7", "8", "9", "C"], ["*", "0", "#", "D"]]
ROW_PINS = [17, 18, 27, 22]
COL_PINS = [23, 24, 25, 4]
RELAY_PIN = 12
BUZZER_PIN = 26

GPIO.setwarnings(False)
GPIO.setmode(GPIO.BCM)
GPIO.setup(RELAY_PIN, GPIO.OUT)
GPIO.output(RELAY_PIN, GPIO.LOW)
GPIO.setup(BUZZER_PIN, GPIO.OUT)
buzzer = GPIO.PWM(BUZZER_PIN, 1000)

for row in ROW_PINS:
    GPIO.setup(row, GPIO.OUT)
    GPIO.output(row, GPIO.LOW)
for col in COL_PINS:
    GPIO.setup(col, GPIO.IN, pull_up_down=GPIO.PUD_DOWN)

def aes_encrypt_gcm(data):
    nonce = get_random_bytes(12)
    cipher = AES.new(AES_KEY, AES.MODE_GCM, nonce=nonce)
    ciphertext, tag = cipher.encrypt_and_digest(data.encode())
    package = {
        "nonce": base64.b64encode(nonce).decode(),
        "tag": base64.b64encode(tag).decode(),
        "ciphertext": base64.b64encode(ciphertext).decode()
    }
    return base64.b64encode(json.dumps(package).encode()).decode()


def aes_decrypt_gcm(encrypted_b64):
    try:
        decoded_json = base64.b64decode(encrypted_b64).decode()
        data = json.loads(decoded_json)
        cipher = AES.new(AES_KEY, AES.MODE_GCM, nonce=base64.b64decode(data["nonce"]))
        plaintext = cipher.decrypt_and_verify(
            base64.b64decode(data["ciphertext"]), base64.b64decode(data["tag"]))
        return plaintext.decode()
    except Exception as e:
        print("[ERROR] AES_DECRYPT FAILED:", e)
        return None

def read_key():
    for i, row in enumerate(ROW_PINS):
        GPIO.output(row, GPIO.HIGH)
        for j, col in enumerate(COL_PINS):
            if GPIO.input(col) == GPIO.HIGH:
                GPIO.output(row, GPIO.LOW)
                return KEYPAD[i][j]
        GPIO.output(row, GPIO.LOW)
    return None

def click_beep():
    buzzer.ChangeFrequency(1000)
    buzzer.start(1)
    sleep(0.1)
    buzzer.stop()

def doorlock_success_beep():
    buzzer.start(1)
    for f in [1000, 1200, 1450]:
        buzzer.ChangeFrequency(f)
        sleep(0.2)
    buzzer.stop()

def fail_beep():
    buzzer.start(50)
    for f in [1520, 1200, 1000]:
        buzzer.ChangeFrequency(f)
        sleep(0.15)
    buzzer.stop()

def warning_beep():
    for _ in range(3):
        buzzer.ChangeFrequency(600)
        buzzer.start(7)
        sleep(0.5)
        buzzer.stop()
        sleep(0.1)

def verify_input_gcm(input_str, device_token, firstauth_token):
    try:
        encrypted_input = aes_encrypt_gcm(input_str)
        encrypted_first_token = aes_encrypt_gcm(firstauth_token)
        payload = {
            "input": encrypted_input,
            "device_token": device_token,
            "firstauth_token": encrypted_first_token,
            "timestamp": datetime.now().isoformat()
        }
        res = requests.post(f"{SERVER_URL}/verify-otp", json=payload)
        if res.ok:
            json_res = res.json()
            print(json_res.get("status"))
            if json_res.get("status") == "success":
                encrypt_idSecondAuth = json_res.get("idSecondAuth")
                print(encrypt_idSecondAuth)
                idSecondAuth = aes_decrypt_gcm(encrypt_idSecondAuth)
                print(idSecondAuth)
                return True, idSecondAuth
        return False, None
    except Exception as e:
        print("[ERROR] verify_input_gcm failed:", e)
        return False, None

# Initial device registration
ssid = "pi"
ip = "192.168.137.229"
encrypted_ip = aes_encrypt_gcm(ip)
payload = {"ssid": ssid, "payload": encrypted_ip}
try:
    res = requests.post(f"{SERVER_URL}/register-pi", json=payload)
    encrypted_token = res.json().get("token")
    token = aes_decrypt_gcm(encrypted_token)
except:
    lcd.lcd_display_string("Token Error", 1)
    sys.exit(1)


while True:
    lcd.lcd_clear()
    lcd.lcd_display_string("Waiting Auth", 1)
    shared_state["auth_result"] = None
    shared_state["firstauth_token"] = None
    auth_ready.clear()
    auth_ready.wait(timeout=60)

    if shared_state["auth_result"] != "success" or not shared_state["firstauth_token"]:
        lcd.lcd_clear()
        lcd.lcd_display_string("Auth Failed", 1)
        sleep(3)
        continue

    firstauth_token = shared_state["firstauth_token"]
    lcd.lcd_clear()
    lcd.lcd_display_string("1FA Passed", 1)
    sleep(2)

    fail_count = 0
    while True:
        lcd.lcd_clear()
        lcd.lcd_display_string("Enter OTP:", 1)
        user_input = ""
        while len(user_input) < 6:
            key = read_key()
            if key:
                if key.isdigit():
                    click_beep()
                    user_input += key
                    lcd.lcd_display_string(user_input, 2)
                elif key == "*":
                    lcd.lcd_clear()
                    lcd.lcd_display_string("Admin Contact", 1)
                    lcd.lcd_display_string("010-2046-8765", 2)
                    sleep(5)
                    lcd.lcd_clear()
                    lcd.lcd_display_string("Enter OTP:", 1)
                    user_input = ""
                elif key == "#":
                    lcd.lcd_display_string("Canceled", 1)
                    sleep(1)
                    user_input = ""
            sleep(0.1)


        lcd.lcd_display_string("Verifying...", 1)
        result, idSecondAuth = verify_input_gcm(user_input, token, firstauth_token)
        if not idSecondAuth:
            result = False
        print(idSecondAuth)
        isopened = 1 if result else 0

        if result:
            lcd.lcd_display_string("Unlocked", 2)
            doorlock_success_beep()
            GPIO.output(RELAY_PIN, GPIO.HIGH)
            sleep(5)
            GPIO.output(RELAY_PIN, GPIO.LOW)
        else:
            lcd.lcd_display_string("Denied", 2)
            fail_beep()
            fail_count += 1
            if fail_count >= 3:
                lcd.lcd_display_string("Too many fails", 1)
                warning_beep()

        log_payload = {
            "device_token": aes_encrypt_gcm(token),
            "idSecondAuth": aes_encrypt_gcm(str(idSecondAuth)),
            "time": aes_encrypt_gcm(datetime.now().isoformat()),
            "result": aes_encrypt_gcm(str(isopened)),
            "count": aes_encrypt_gcm(str(fail_count))
        }
        try:
            requests.post(f"{SERVER_URL}/open-record", json=log_payload)
        except Exception as e:
            print("[ERROR] Logging failed:", e)

        if result:
            break
        sleep(2)
