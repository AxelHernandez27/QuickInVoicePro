package com.example.workadministration

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.ui.NavigationUtil
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Usa la función utilitaria, pasando el id de la opción actual
        NavigationUtil.setupNavigation(this, bottomNav, R.id.nav_home)
    }
}
