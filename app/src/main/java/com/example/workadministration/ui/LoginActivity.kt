package com.example.workadministration.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.HomeActivity
import com.example.workadministration.R
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    Thread {
                        try {
                            val googleAccount = account.account
                            if (googleAccount != null) {
                                val token = GoogleAuthUtil.getToken(
                                    applicationContext,
                                    googleAccount,
                                    "oauth2:https://www.googleapis.com/auth/calendar"
                                )
                                Log.d("Token", "Token obtenido: $token")

                                // Inicia HomeActivity PASANDO el token
                                runOnUiThread {
                                    val intent = Intent(this, HomeActivity::class.java)
                                    val prefs = getSharedPreferences("my_prefs", MODE_PRIVATE)
                                    prefs.edit().putString("ACCESS_TOKEN", token).apply()
                                    startActivity(intent)
                                    finish()
                                }
                            } else {
                                Log.e("TokenError", "Account es null")
                                // Si no hay cuenta, iniciar sin token
                                runOnUiThread {
                                    startActivity(Intent(this, HomeActivity::class.java))
                                    finish()
                                }
                            }
                        } catch (e: UserRecoverableAuthException) {
                            Log.e("TokenError", "Se requieren permisos del usuario: ${e.message}")
                            e.intent?.let {
                                startActivityForResult(it, 1001)
                            }
                        } catch (e: Exception) {
                            Log.e("TokenError", "Error obteniendo token: ${e.message}")
                            runOnUiThread {
                                startActivity(Intent(this, HomeActivity::class.java))
                                finish()
                            }
                        }
                    }.start()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Google login failed", Toast.LENGTH_SHORT).show()
                }
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google Sign-In error", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Si ya hay sesión activa, ir directo a Home
        if (auth.currentUser != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val emailInput: EditText = findViewById(R.id.emailInput)
        val passwordInput: EditText = findViewById(R.id.passwordInput)
        val loginButton: Button = findViewById(R.id.loginButton)
        val registerLink: TextView = findViewById(R.id.registerLink)
        val googleButton: SignInButton = findViewById(R.id.googleSignInButton)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Login error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        googleButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }
}
