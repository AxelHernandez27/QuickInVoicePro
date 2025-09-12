package com.example.workadministration.ui.invoice

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
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
import java.util.*

class InvoiceActivity : AppCompatActivity(), AddCustomerBottomSheet.OnCustomerAddedListener,
    AddInvoiceBottomSheet.OnInvoiceSavedListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchInvoice: EditText
    private lateinit var adapter: InvoiceAdapter
    private val invoiceList = mutableListOf<Invoice>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCustomerAdded(customer: Customer) {}

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tickets)

        val btnAgregar = findViewById<ImageButton>(R.id.btnAgregarTicket)
        btnAgregar.setOnClickListener {
            val bottomSheet = AddInvoiceBottomSheet()
            bottomSheet.show(supportFragmentManager, "AddInvoiceBottomSheet")
        }

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
        invoiceList.clear()

        val displayFormat = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US)
        displayFormat.timeZone = TimeZone.getTimeZone("America/Mexico_City")

        db.collection("invoices")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { invoicesSnapshot ->

                val tempList = mutableListOf<Invoice>()
                var remaining = invoicesSnapshot.size()

                if (remaining == 0) {
                    invoiceList.clear()
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                for (invoiceDoc in invoicesSnapshot) {
                    val invoiceId = invoiceDoc.id
                    val customerId = invoiceDoc.getString("customerId") ?: ""
                    val customerName = invoiceDoc.getString("customerName") ?: ""
                    val customerAddress = invoiceDoc.getString("customerAddress") ?: ""
                    val dateField = invoiceDoc.get("date")
                    val dateValue = if (dateField is com.google.firebase.Timestamp) dateField.toDate() else Date()

                    val extraCharges = invoiceDoc.getDouble("extraCharges") ?: 0.0
                    val notes = invoiceDoc.getString("notes") ?: ""
                    val total = invoiceDoc.getDouble("total") ?: 0.0
                    val totalPurchasePrice = invoiceDoc.getDouble("totalPurchasePrice") ?: 0.0

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
                                date = displayFormat.format(dateValue), // <-- aquí el formato MM/dd/yyyy hh:mm a
                                extraCharges = extraCharges,
                                notes = notes,
                                total = total,
                                totalPurchasePrice = totalPurchasePrice,
                                products = products
                            )

                            tempList.add(invoice)
                            remaining--

                            if (remaining == 0) {
                                // Ordenamos por fecha descending usando el Date real
                                tempList.sortByDescending {
                                    displayFormat.parse(it.date)
                                }
                                invoiceList.clear()
                                invoiceList.addAll(tempList)
                                adapter.notifyDataSetChanged()
                            }
                        }
                }
            }
    }


    private fun filterInvoices(text: String) {
        val dateFormat = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US)
        val filteredList = invoiceList.filter {
            it.customerName.contains(text, ignoreCase = true) ||
                    dateFormat.format(it.date).contains(text, ignoreCase = true)
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
        val detailsRef = db.collection("invoices").document(invoice.id).collection("invoiceDetails")

        detailsRef.get().addOnSuccessListener { snapshot ->
            for (doc in snapshot.documents) {
                doc.reference.delete()
            }

            // Primero actualizar el reporte mensual
            updateMonthlyReportOnDelete(invoice)

            // Luego eliminar la factura
            db.collection("invoices").document(invoice.id)
                .delete()
                .addOnSuccessListener {
                    invoiceList.remove(invoice)
                    adapter.updateList(invoiceList)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error deleting invoice", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Método para actualizar reporte mensual al eliminar
    private fun updateMonthlyReportOnDelete(invoice: Invoice) {
        val calendar = Calendar.getInstance().apply {
            time = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US).parse(invoice.date) ?: Date()
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val reportId = "${year}_$month"
        val reportRef = db.collection("reports").document(reportId)

        val totalTickets = invoice.total
        val totalMaterials = invoice.totalPurchasePrice
        val profit = totalTickets - totalMaterials

        db.runTransaction { transaction ->
            val snapshot = transaction.get(reportRef)
            val currentTotalTickets = snapshot.getDouble("totalTickets") ?: 0.0
            val currentTotalMaterials = snapshot.getDouble("totalMaterials") ?: 0.0
            val currentProfit = snapshot.getDouble("profit") ?: 0.0

            val newTotalTickets = (currentTotalTickets - totalTickets).coerceAtLeast(0.0)
            val newTotalMaterials = (currentTotalMaterials - totalMaterials).coerceAtLeast(0.0)
            val newProfit = (currentProfit - profit).coerceAtLeast(0.0)

            transaction.set(
                reportRef, mapOf(
                    "year" to year,
                    "month" to month,
                    "totalTickets" to newTotalTickets,
                    "totalMaterials" to newTotalMaterials,
                    "profit" to newProfit
                )
            )
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
