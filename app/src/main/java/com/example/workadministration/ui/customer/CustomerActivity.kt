package com.example.workadministration.ui.customer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.example.workadministration.ui.NavigationUtil
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class CustomerActivity : AppCompatActivity() {

    private lateinit var recyclerCustomers: RecyclerView
    private lateinit var serchCustomer: EditText
    private lateinit var adapter: CustomerAdapter
    private val customersList = mutableListOf<Customer>()
    private val db = FirebaseFirestore.getInstance()

    private val addCustomerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            getCustomers()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer)

        val buttonAdd = findViewById<Button>(R.id.btnAgregarCliente)
        buttonAdd.setOnClickListener {
            val intent = Intent(this, AddCustomerActivity::class.java)
            addCustomerLauncher.launch(intent)
        }

        recyclerCustomers = findViewById(R.id.recyclerClientes)
        serchCustomer = findViewById(R.id.buscarCliente)

        adapter = CustomerAdapter(customersList, { customer ->
            eliminarCliente(customer)
        }, { customer ->
            val intent = Intent(this, EditCustomerActivity::class.java).apply {
                putExtra("id", customer.id)
                putExtra("fullname", customer.fullname)
                putExtra("address", customer.address)
                putExtra("phone", customer.phone)
                putExtra("email", customer.email)
                putExtra("notes", customer.notes)
            }
            addCustomerLauncher.launch(intent)
        })

        recyclerCustomers.layoutManager = LinearLayoutManager(this)
        recyclerCustomers.adapter = adapter

        getCustomers()

        serchCustomer.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filtrarClientes(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Integración de la navegación
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        NavigationUtil.setupNavigation(this, bottomNav, R.id.nav_customers)
    }

    private fun getCustomers() {
        db.collection("customers")
            .get()
            .addOnSuccessListener { documents ->
                customersList.clear()
                for (document in documents) {
                    val customer = document.toObject(Customer::class.java).copy(id = document.id)
                    customersList.add(customer)
                }
                adapter.actualizarLista(customersList)
            }
    }

    private fun filtrarClientes(texto: String) {
        val listaFiltrada = customersList.filter {
            it.fullname.contains(texto, ignoreCase = true) ||
                    it.email.contains(texto, ignoreCase = true)
        }
        adapter.actualizarLista(listaFiltrada)
    }

    private fun eliminarCliente(cliente: Customer) {
        db.collection("customers").document(cliente.id)
            .delete()
            .addOnSuccessListener {
                customersList.remove(cliente)
                adapter.actualizarLista(customersList)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting customer", Toast.LENGTH_SHORT).show()
            }
    }

}
