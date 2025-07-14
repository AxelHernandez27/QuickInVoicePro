package com.example.workadministration.ui.invoice

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot

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

                if (documents.isEmpty) {
                    adapter.updateList(invoiceList)
                    return@addOnSuccessListener
                }

                // Cargar detalles de productos para cada factura
                val totalInvoices = documents.size()
                var loadedInvoices = 0

                for (document in documents) {
                    val invoiceId = document.id
                    val data = document.data

                    val timestamp = data["date"] as? com.google.firebase.Timestamp
                    val dateString = timestamp?.toDate()?.let {
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(it)
                    } ?: ""

                    val invoice = Invoice(
                        id = invoiceId,
                        customerId = data["customerId"] as? String ?: "",
                        customerName = data["customerName"] as? String ?: "",
                        customerAddress = data["customerAddress"] as? String ?: "",
                        date = dateString,
                        extraCharges = (data["extraCharges"] as? Double) ?: 0.0,
                        notes = data["notes"] as? String ?: "",
                        total = (data["total"] as? Double) ?: 0.0,
                        products = emptyList()
                    )

                    // Ahora cargamos los detalles de productos desde la subcolección
                    db.collection("invoices").document(invoiceId).collection("invoiceDetails")
                        .get()
                        .addOnSuccessListener { productDocs ->
                            val productList = productDocs.mapNotNull { detailDoc ->
                                val detail = detailDoc.data
                                val quantity = (detail["quantity"] as? Long)?.toInt() ?: 1
                                val price = (detail["price"] as? Double) ?: 0.0
                                val name = detail["name"] as? String ?: ""
                                val productId = detail["productId"] as? String ?: ""
                                ProductDetail(
                                    productId = productId,
                                    name = name,
                                    quantity = quantity,
                                    price = price
                                )
                            }

                            invoiceList.add(invoice.copy(products = productList))

                            loadedInvoices++
                            if (loadedInvoices == totalInvoices) {
                                adapter.updateList(invoiceList)
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error loading invoice details", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load invoices", Toast.LENGTH_SHORT).show()
            }
    }
}
