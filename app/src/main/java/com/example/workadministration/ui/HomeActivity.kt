package com.example.workadministration

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.ui.NavigationUtil
import com.example.workadministration.ui.appointment.AppointmentFragment
import com.example.workadministration.ui.quote.QuoteFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {

    private lateinit var btnAppointments: Button
    private lateinit var btnQuotes: Button
    private lateinit var contentContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_home)

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
}
