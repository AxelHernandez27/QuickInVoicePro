package com.example.workadministration

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.ui.NavigationUtil
import com.example.workadministration.ui.appointment.AppointmentFragment
import com.example.workadministration.ui.quote.QuoteFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

import android.widget.PopupMenu
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.workadministration.ui.LoginActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.common.api.Scope

class HomeActivity : AppCompatActivity() {

    private lateinit var btnAppointments: Button
    private lateinit var btnQuotes: Button
    private lateinit var contentContainer: FrameLayout

    private var accessToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_home)

        // Obtener el token que viene por Intent
        accessToken = getSharedPreferences("my_prefs", MODE_PRIVATE).getString("ACCESS_TOKEN", null)
        Log.d("HomeActivity", "Token leído de prefs: $accessToken")

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        NavigationUtil.setupNavigation(this, bottomNav, R.id.nav_home)

        btnAppointments = findViewById(R.id.btnAppointments)
        btnQuotes = findViewById(R.id.btnQuotes)
        contentContainer = findViewById(R.id.contentContainer)

        // Mostrar citas por defecto
        showAppointments()

        btnAppointments.setOnClickListener {
            showAppointments()
        }

        btnQuotes.setOnClickListener {
            showQuotes()
        }

        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        btnSettings.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_config, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_logout -> {
                        logOut()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

    }

    private fun setActiveButton(selected: Button, other: Button) {
        // Animar fondo cambiando alpha (opcional)
        selected.animate().alpha(1f).setDuration(250).start()
        other.animate().alpha(0.6f).setDuration(250).start()

        // Cambiar background y texto
        selected.setBackgroundResource(R.drawable.btn_green_rounded)
        selected.setTextColor(resources.getColor(android.R.color.white))

        other.setBackgroundResource(R.drawable.btn_outline_gray)
        other.setTextColor(resources.getColor(android.R.color.black))
    }

    private fun showAppointments() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, AppointmentFragment())
            .commit()
        setActiveButton(btnAppointments, btnQuotes)
    }

    private fun showQuotes() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, QuoteFragment())
            .commit()
        setActiveButton(btnQuotes, btnAppointments)
    }

    fun logOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInClient.revokeAccess().addOnCompleteListener {
            // También cierra la sesión de Firebase
            FirebaseAuth.getInstance().signOut()

            Toast.makeText(this, "Sesión cerrada y permisos revocados.", Toast.LENGTH_SHORT).show()

            // Redirige al login
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }


}
