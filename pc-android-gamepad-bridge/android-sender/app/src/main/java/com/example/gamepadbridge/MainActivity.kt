package com.example.gamepadbridge

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startInputService()
    }

    private fun startInputService() {
        val serviceIntent = Intent(this, InputService::class.java)
        startService(serviceIntent)
    }
}