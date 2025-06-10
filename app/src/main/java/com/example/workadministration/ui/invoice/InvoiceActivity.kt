package com.example.workadministration.ui.invoice

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.example.workadministration.ui.customer.Customer
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class InvoiceActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchInvoice: EditText
    private lateinit var adapter: InvoiceAdapter
    private val invoiceList = mutableListOf<Invoice>()
    private val db = FirebaseFirestore.getInstance()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tickets)

        recyclerView = findViewById(R.id.recyclerTickets)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = InvoiceAdapter(invoiceList)
        recyclerView.adapter = adapter
        searchInvoice = findViewById(R.id.buscarTicket)

        getInvoices()

        searchInvoice.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterInvoices(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun getInvoices() {
        val db = FirebaseFirestore.getInstance()

        db.collection("invoices").get().addOnSuccessListener { invoicesSnapshot ->
            invoiceList.clear()

            for (invoiceDoc in invoicesSnapshot) {
                val invoiceId = invoiceDoc.id
                val customerId = invoiceDoc.getString("customerId") ?: ""
                val customerName = invoiceDoc.getString("customerName") ?: ""
                val customerAddress = invoiceDoc.getString("customerAddress") ?: ""
                val date = invoiceDoc.getTimestamp("date")?.toDate()?.let { dateObj ->
                    val formatter = SimpleDateFormat("EEE dd MMM", Locale.ENGLISH)
                    formatter.format(dateObj)
                } ?: ""
                val extraCharges = invoiceDoc.getDouble("extraCharges") ?: 0.0
                val notes = invoiceDoc.getString("notes") ?: ""
                val total = invoiceDoc.getDouble("total") ?: 0.0

                // Obtener subcolección de productos
                db.collection("invoices").document(invoiceId)
                    .collection("invoiceDetails")
                    .get()
                    .addOnSuccessListener { detailsSnapshot ->
                        val products = detailsSnapshot.map { detailDoc ->
                            ProductDetail(
                                productId = detailDoc.getString("productId") ?: "",
                                name = detailDoc.getString("productName") ?: "",
                                price = detailDoc.getDouble("price") ?: 0.0
                            )
                        }

                        val invoice = Invoice(
                            id = invoiceId,
                            customerId = customerId,
                            customerName = customerName,
                            customerAddress = customerAddress,
                            date = date,
                            extraCharges = extraCharges,
                            notes = notes,
                            total = total,
                            products = products
                        )

                        invoiceList.add(invoice)
                        adapter.notifyDataSetChanged()
                    }
            }
        }
    }

    private fun filterInvoices(text: String) {
        val filteredList = invoiceList.filter {
            it.customerName.contains(text, ignoreCase = true) ||
                    it.date.contains(text, ignoreCase = true)
        }
        adapter.updateList(filteredList)
    }

    private fun deleteInvoice(invoice: Invoice) {
        db.collection("invoices").document(invoice.id)
            .delete()
            .addOnSuccessListener {
                invoiceList.remove(invoice)
                adapter.updateList(invoiceList)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting invoice", Toast.LENGTH_SHORT).show()
            }
    }
}
