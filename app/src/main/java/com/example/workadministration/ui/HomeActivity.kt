package com.example.workadministration

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.ui.customer.CustomerActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Ya estás en Home, no haces nada
                    true
                }
                R.id.nav_customers -> {
                    // Redirige a CustomerActivity
                    val intent = Intent(this, CustomerActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }
}
