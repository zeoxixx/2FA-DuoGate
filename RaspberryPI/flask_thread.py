
from flask import Flask, request, jsonify
import threading
import base64
import json
from Crypto.Cipher import AES

auth_ready = threading.Event()
auth_result = None
firstauth_token = None

shared_state = {
    "auth_result": None,
    "firstauth_token": None
}

AES_KEY = b'!\xca\xd6h\xb3\xff\x16\x19a=H4\xf1\xd2p\xa0\xd0\x14+\xcci\x0b\xea\xb3\x113b\xac\x8f\x074Y'

def aes_decrypt_gcm(encrypted_b64):
    try:
        decoded_json = base64.b64decode(encrypted_b64).decode()
        data = json.loads(decoded_json)
        cipher = AES.new(AES_KEY, AES.MODE_GCM, nonce=base64.b64decode(data["nonce"]))
        plaintext = cipher.decrypt_and_verify(
            base64.b64decode(data["ciphertext"]),
            base64.b64decode(data["tag"])
        )
        return plaintext.decode()
    except Exception as e:
        print("[ERROR] AES_DECRYPT FAILED:", e)
        return None

app = Flask(__name__)

@app.route("/notify-firstauth", methods=["POST"])
def notify_firstauth():
    global auth_result, firstauth_token, shared_state
    try:
        data = request.get_json()
        encrypted_status = data.get("status")
        encrypted_token = data.get("firstauth_token")

        auth_result = aes_decrypt_gcm(encrypted_status)
        firstauth_token = aes_decrypt_gcm(encrypted_token)

        shared_state["auth_result"] = auth_result
        shared_state["firstauth_token"] = firstauth_token

        print("state:", auth_result)
        print("token:", firstauth_token)

        auth_ready.set()
        return jsonify({"status": "received"})
    except Exception as e:
        return jsonify({"status": "fail", "message": str(e)}), 400

def start_flask():
    app.run(host="0.0.0.0", port=5001)

#if __name__ == "__main__":
    #threading.Thread(target=start_flask, daemon=True).start()
