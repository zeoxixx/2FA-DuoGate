#model/db.py
#설정된 정보를 바탕으로 DB연결을 하는 파일

import mysql.connector
from config.settings import DB_CONFIG

def start_connection() :
    return mysql.connector.connect(
        host=DB_CONFIG["host"],
        user=DB_CONFIG["user"],
        password=DB_CONFIG["password"],
        database=DB_CONFIG["database"]
)
