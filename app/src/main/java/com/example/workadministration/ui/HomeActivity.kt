package com.example.workadministration

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.ui.customer.CustomerActivity
import com.example.workadministration.ui.invoice.InvoiceActivity
import com.example.workadministration.ui.product.ProductActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    true
                }
                R.id.nav_products -> {
                    val intent = Intent(this, ProductActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.nav_customers -> {
                    val intent = Intent(this, CustomerActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.nav_tickets -> {
                    val intent = Intent(this, InvoiceActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }
}
