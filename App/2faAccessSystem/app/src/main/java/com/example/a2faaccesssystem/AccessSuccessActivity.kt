package com.example.a2faaccesssystem

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class AccessSuccessActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_access_success)

        // 5초 후 앱 종료
        Handler(Looper.getMainLooper()).postDelayed({
            finishAffinity()  // 앱 완전 종료
        }, 5000)
    }
}