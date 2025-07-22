package com.example.workadministration

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.ui.appointment.AppointmentActivity
import com.example.workadministration.ui.quote.QuoteActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var btnAppointments: Button
    private lateinit var btnQuotes: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_home)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, QuoteFragment())
            .commit()

        btnAppointments = findViewById(R.id.btnAppointments)
        btnQuotes = findViewById(R.id.btnQuotes)

        btnAppointments.setOnClickListener {
            val intent = Intent(this, AppointmentActivity::class.java)
            startActivity(intent)
        }

        btnQuotes.setOnClickListener {
            val intent = Intent(this, QuoteActivity::class.java)
            startActivity(intent)
        }
    }
}
