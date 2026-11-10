#api/app.py
#Flask 서버의 진입점, 각 api 파일을 불러오고 실행

from flask import Flask

#blueprint 등록
from api.verify_mac import verify_mac_bp
from api.issue_key import issue_key_bp
from api.register_user import register_user_bp
from api.register_mac import register_mac_bp
from api.login import login_bp
from api.request_otp import request_otp_bp
from api.register_pi import register_pi_bp
from api.verify_otp import verify_otp_bp
from api.open_record import open_record_bp

#flask 애플리케이션 객체 생성
app = Flask(__name__)

#api 모듈 등록 후 접근 가능하게 만듦
app.register_blueprint(verify_mac_bp)
app.register_blueprint(issue_key_bp)
app.register_blueprint(register_user_bp)
app.register_blueprint(register_mac_bp)
app.register_blueprint(login_bp)
app.register_blueprint(request_otp_bp)
app.register_blueprint(register_pi_bp)
app.register_blueprint(verify_otp_bp)
app.register_blueprint(open_record_bp)


#직접 실행될 때만 서버 실행, 외부 장치 접근 가능 위한 호스트 ip 설정
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
