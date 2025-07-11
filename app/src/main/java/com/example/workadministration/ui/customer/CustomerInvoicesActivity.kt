package com.example.workadministration.ui.invoice

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.google.firebase.firestore.FirebaseFirestore

class CustomerInvoicesActivity : AppCompatActivity() {

    private lateinit var recyclerInvoices: RecyclerView
    private lateinit var adapter: InvoiceAdapter
    private val db = FirebaseFirestore.getInstance()
    private val invoiceList = mutableListOf<Invoice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_invoices)

        val customerId = intent.getStringExtra("clienteId") ?: return
        val customerName = intent.getStringExtra("clienteNombre") ?: "Customer"

        findViewById<TextView>(R.id.tvCustomerInvoiceTitle).text = "Invoices for $customerName"

        recyclerInvoices = findViewById(R.id.recyclerCustomerInvoices)
        recyclerInvoices.layoutManager = LinearLayoutManager(this)

        adapter = InvoiceAdapter(
            invoices = invoiceList,
            context = this,
            onEditClick = {},
            onDeleteClick = {}
        )
        recyclerInvoices.adapter = adapter

        loadInvoicesForCustomer(customerId)
    }

    private fun loadInvoicesForCustomer(customerId: String) {
        db.collection("invoices")
            .whereEqualTo("customerId", customerId)
            .get()
            .addOnSuccessListener { documents ->
                invoiceList.clear()
                for (document in documents) {
                    val data = document.data
                    val timestamp = data["date"] as? com.google.firebase.Timestamp
                    val dateString = timestamp?.toDate()?.let {
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(it)
                    } ?: ""

                    val invoice = Invoice(
                        id = document.id,
                        customerId = data["customerId"] as? String ?: "",
                        customerName = data["customerName"] as? String ?: "",
                        customerAddress = data["customerAddress"] as? String ?: "",
                        date = dateString,
                        extraCharges = (data["extraCharges"] as? Double) ?: 0.0,
                        notes = data["notes"] as? String ?: "",
                        products = emptyList(), // Puedes adaptar para mapear los productos si quieres
                        quantity = data["quantity"] as? Number ?: 0,
                        total = (data["total"] as? Double) ?: 0.0
                    )

                    invoiceList.add(invoice)
                }
                adapter.updateList(invoiceList)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load invoices", Toast.LENGTH_SHORT).show()
            }
    }

}
