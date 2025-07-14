package com.example.workadministration.ui.invoice

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.registerForActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.example.workadministration.ui.NavigationUtil
import com.example.workadministration.ui.customer.AddCustomerBottomSheet
import com.example.workadministration.ui.customer.Customer
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class InvoiceActivity : AppCompatActivity(), AddCustomerBottomSheet.OnCustomerAddedListener, AddInvoiceBottomSheet.OnInvoiceSavedListener {


    private lateinit var recyclerView: RecyclerView
    private lateinit var searchInvoice: EditText
    private lateinit var adapter: InvoiceAdapter
    private val invoiceList = mutableListOf<Invoice>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCustomerAdded(customer: Customer) {
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tickets)

        val btnAgregar = findViewById<Button>(R.id.btnAgregarTicket)
        btnAgregar.setOnClickListener {
            val bottomSheet = AddInvoiceBottomSheet()
            bottomSheet.show(supportFragmentManager, "AddInvoiceBottomSheet")
        }

        recyclerView = findViewById(R.id.recyclerTickets)
        recyclerView = findViewById(R.id.recyclerTickets)
        recyclerView = findViewById(R.id.recyclerTickets)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = InvoiceAdapter(
            invoiceList,
            this,
            onDeleteClick = { invoice -> confirmDelete(invoice) },
            onEditClick = { invoice -> openEditScreen(invoice) },
        )

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

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        NavigationUtil.setupNavigation(this, bottomNav, R.id.nav_tickets)
    }

    override fun onInvoiceSaved() {
        getInvoices()
    }

    private fun getInvoices() {
        val db = FirebaseFirestore.getInstance()

        db.collection("invoices")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { invoicesSnapshot ->

            invoiceList.clear()

            for (invoiceDoc in invoicesSnapshot) {
                val dateObj = invoiceDoc.getTimestamp("date")?.toDate()
                val invoiceId = invoiceDoc.id
                val customerId = invoiceDoc.getString("customerId") ?: ""
                val customerName = invoiceDoc.getString("customerName") ?: ""
                val customerAddress = invoiceDoc.getString("customerAddress") ?: ""
                val timestamp = invoiceDoc.getTimestamp("date")
                val dateFormatted = if (timestamp != null) {
                    val dateObj = timestamp.toDate()
                    val formatter = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale("es", "MX"))
                    formatter.timeZone = TimeZone.getTimeZone("America/Mexico_City") // Hora centro México
                    formatter.format(dateObj)
                } else {
                    ""
                }

                val extraCharges = invoiceDoc.getDouble("extraCharges") ?: 0.0
                val notes = invoiceDoc.getString("notes") ?: ""
                val total = invoiceDoc.getDouble("total") ?: 0.0

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
                            date = dateFormatted,
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
            it.customerName.contains(text, ignoreCase = true)
        }
        adapter.updateList(filteredList)
    }

    private fun openEditScreen(invoice: Invoice) {
        val editInvoiceSheet = EditInvoiceBottomSheet.newInstance(invoice.id)
        editInvoiceSheet.setOnInvoiceUpdatedListener(object : EditInvoiceBottomSheet.OnInvoiceUpdatedListener {
            override fun onInvoiceUpdated() {
                getInvoices()
            }
        })
        editInvoiceSheet.show(supportFragmentManager, "EditInvoiceBottomSheet")
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

    private fun confirmDelete(invoice: Invoice) {
        val alert = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Invoice")
            .setMessage("Are you sure you want to delete this invoice?")
            .setPositiveButton("Yes") { _, _ ->
                deleteInvoice(invoice)
            }
            .setNegativeButton("Cancel", null)
            .create()
        alert.show()
    }
}
