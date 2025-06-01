package com.example.workadministration;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeActivity extends AppCompatActivity  {
    BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inicio);

        bottomNavigation = findViewById(R.id.bottomNavigation);

        bottomNavigation.setOnItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.nav_home:
                    startActivity(new Intent(this, HomeActivity.class));
                    return true;
                case R.id.nav_products:
                    startActivity(new Intent(this, ProductsActivity.class));
                    return true;
                case R.id.nav_customers:
                    startActivity(new Intent(this, CustomersActivity.class));
                    return true;
                case R.id.nav_tickets:
                    startActivity(new Intent(this, TicketsActivity.class));
                    return true;
            }
            return false;
        });
    }
}
