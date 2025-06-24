package com.example.workadministration.ui

import android.app.Activity
import android.content.Intent
import com.example.workadministration.HomeActivity
import com.example.workadministration.R
import com.example.workadministration.ui.product.ProductActivity
import com.example.workadministration.ui.customer.CustomerActivity
import com.example.workadministration.ui.invoice.InvoiceActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

object NavigationUtil {

    fun setupNavigation(activity: Activity, navView: BottomNavigationView, currentMenuId: Int) {

        navView.selectedItemId = currentMenuId

        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    if (currentMenuId != R.id.nav_home) {
                        activity.startActivity(Intent(activity, HomeActivity::class.java))
                        activity.finish()
                    }
                    true
                }
                R.id.nav_products -> {
                    if (currentMenuId != R.id.nav_products) {
                        activity.startActivity(Intent(activity, ProductActivity::class.java))
                        activity.finish()
                    }
                    true
                }
                R.id.nav_customers -> {
                    if (currentMenuId != R.id.nav_customers) {
                        activity.startActivity(Intent(activity, CustomerActivity::class.java))
                        activity.finish()
                    }
                    true
                }
                R.id.nav_tickets -> {
                    if (currentMenuId != R.id.nav_tickets) {
                        activity.startActivity(Intent(activity, InvoiceActivity::class.java))
                        activity.finish()
                    }
                    true
                }
                else -> false
            }
        }
    }
}
