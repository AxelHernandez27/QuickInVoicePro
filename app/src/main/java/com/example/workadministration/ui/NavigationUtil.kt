package com.example.workadministration.ui

import android.app.Activity
import android.content.Intent
import com.example.workadministration.HomeActivity
import com.example.workadministration.R
import com.example.workadministration.ui.product.ProductActivity
import com.example.workadministration.ui.customer.CustomerActivity
import com.example.workadministration.ui.dashboard.DashboardActivity
import com.example.workadministration.ui.invoice.InvoiceActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

object NavigationUtil {

    fun setupNavigation(activity: Activity, navView: BottomNavigationView, currentMenuId: Int) {

        navView.selectedItemId = currentMenuId

        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    if (currentMenuId != R.id.nav_home) {
                        val intent = Intent(activity, HomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        activity.startActivity(intent)
                        activity.overridePendingTransition(0, 0)  // Sin animación
                        activity.finish()
                    }
                    true
                }
                R.id.nav_products -> {
                    if (currentMenuId != R.id.nav_products) {
                        val intent = Intent(activity, ProductActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        activity.startActivity(intent)
                        activity.overridePendingTransition(0, 0)
                        activity.finish()
                    }
                    true
                }
                R.id.nav_customers -> {
                    if (currentMenuId != R.id.nav_customers) {
                        val intent = Intent(activity, CustomerActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        activity.startActivity(intent)
                        activity.overridePendingTransition(0, 0)
                        activity.finish()
                    }
                    true
                }

                R.id.nav_tickets -> {
                    if (currentMenuId != R.id.nav_tickets) {
                        val intent = Intent(activity, InvoiceActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        activity.startActivity(intent)
                        activity.overridePendingTransition(0, 0)
                        activity.finish()
                    }
                    true
                }

                R.id.nav_dashboard-> {
                    if (currentMenuId != R.id.nav_dashboard) {
                        val intent = Intent(activity, DashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        activity.startActivity(intent)
                        activity.overridePendingTransition(0, 0)
                        activity.finish()
                    }
                    true
                }

                else -> false
            }
        }
    }
}
