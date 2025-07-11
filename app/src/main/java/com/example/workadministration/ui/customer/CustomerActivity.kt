package com.example.workadministration.ui.customer

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.example.workadministration.ui.NavigationUtil
import com.example.workadministration.ui.invoice.CustomerInvoicesActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class CustomerActivity : AppCompatActivity(),
    AddCustomerBottomSheet.OnCustomerAddedListener,
    EditCustomerBottomSheet.OnCustomerUpdatedListener {

    private lateinit var recyclerCustomers: RecyclerView
    private lateinit var searchCustomer: EditText
    private lateinit var adapter: CustomerAdapter
    private val customersList = mutableListOf<Customer>()
    private val db = FirebaseFirestore.getInstance()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer)

        val addButton = findViewById<Button>(R.id.btnAgregarCliente)
        addButton.setOnClickListener {
            val bottomSheet = AddCustomerBottomSheet()
            bottomSheet.show(supportFragmentManager, "AddCustomerBottomSheet")
        }

        recyclerCustomers = findViewById(R.id.recyclerClientes)
        searchCustomer = findViewById(R.id.buscarCliente)
        adapter = CustomerAdapter(
            clientes = customersList,
            onDeleteClick = { customer ->
                eliminarCliente(customer)
            },
            onEditClick = { customer ->
                val editBottomSheet = EditCustomerBottomSheet(customer)
                editBottomSheet.show(supportFragmentManager, "EditCustomerBottomSheet")
            },
            onItemClick = { customer ->
                // Ir a la pantalla de tickets
                val intent = Intent(this, CustomerInvoicesActivity::class.java)
                intent.putExtra("clienteId", customer.id)
                intent.putExtra("clienteNombre", customer.fullname)
                startActivity(intent)
            }
        )


        recyclerCustomers.layoutManager = LinearLayoutManager(this)
        recyclerCustomers.adapter = adapter

        getCustomers()

        searchCustomer.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filtrarClientes(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

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
        val filteredList = customersList.filter {
            it.fullname.contains(texto, ignoreCase = true) ||
                    it.email.contains(texto, ignoreCase = true)
        }
        adapter.actualizarLista(filteredList)
    }

    private fun eliminarCliente(customer: Customer) {
        AlertDialog.Builder(this)
            .setTitle("Confirmation")
            .setMessage("Do you want to delete ${customer.fullname}?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("customers").document(customer.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Customer deleted", Toast.LENGTH_SHORT).show()
                        customersList.remove(customer)
                        adapter.actualizarLista(customersList)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error deleting customer", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCustomerAdded(customer: Customer) {
        db.collection("customers")
            .add(customer)
            .addOnSuccessListener {
                Toast.makeText(this, "Customer added successfully", Toast.LENGTH_SHORT).show()
                getCustomers()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onCustomerUpdated(updatedCustomer: Customer) {
        db.collection("customers").document(updatedCustomer.id)
            .set(updatedCustomer)
            .addOnSuccessListener {
                Toast.makeText(this, "Customer updated successfully", Toast.LENGTH_SHORT).show()
                getCustomers()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error updating customer", Toast.LENGTH_SHORT).show()
            }
    }
}
