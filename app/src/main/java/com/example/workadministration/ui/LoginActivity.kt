package com.example.workadministration.ui

import com.google.firebase.FirebaseApp
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import com.example.workadministration.HomeActivity
import com.example.workadministration.R
import com.google.android.gms.common.api.Api.Client
import com.google.firebase.auth.FirebaseAuth


class LoginActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_login)

        val loginButton: Button = findViewById(R.id.loginButton)
        loginButton.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

}
