package com.example.workadministration.ui.quote

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
import java.util.Locale
import java.util.TimeZone

class QuoteActivity : AppCompatActivity(), AddCustomerBottomSheet.OnCustomerAddedListener, AddQuoteBottomSheet.OnQuoteSavedListener {


    private lateinit var recyclerView: RecyclerView
    private lateinit var searchQuote: EditText
    private lateinit var adapter: QuoteAdapter
    private val quoteList = mutableListOf<Quote>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCustomerAdded(customer: Customer) {
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quote)

        val btnAgregar = findViewById<ImageButton>(R.id.btnAgregarQuote)
        btnAgregar.setOnClickListener {
            val bottomSheet = AddQuoteBottomSheet(this)
            bottomSheet.show(supportFragmentManager, "AddQuoteBottomSheet")
        }

        recyclerView = findViewById(R.id.recyclerQuotes)
        recyclerView = findViewById(R.id.recyclerQuotes)
        recyclerView = findViewById(R.id.recyclerQuotes)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = QuoteAdapter(
            quoteList,
            this,
            onDeleteClick = { quote -> confirmDelete(quote) },
            onEditClick = { quote -> openEditScreen(quote) },
            onItemClick = { quote -> confirmConvert(quote) } // 👈 aquí agregas la conversión

        )

        recyclerView.adapter = adapter
        searchQuote = findViewById(R.id.buscarQuote)

        getQuotes()

        searchQuote.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterQuotes(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        NavigationUtil.setupNavigation(this, bottomNav, R.id.nav_home)
    }

    override fun onQuoteSaved() {
        getQuotes()
    }
    private fun convertQuoteToInvoice(quote: Quote) {
        val invoiceId = db.collection("invoices").document().id

        // Mapear productos de la quote a los productos de la invoice
        val invoiceProducts = quote.products.map { product ->
            com.example.workadministration.ui.invoice.ProductDetail(
                productId = product.productId,
                name = product.name,       // Aquí usamos product.name como en getQuotes()
                price = product.price
            )
        }

        val invoice = com.example.workadministration.ui.invoice.Invoice(
            id = invoiceId,
            customerId = quote.customerId,
            customerName = quote.customerName,
            customerAddress = quote.customerAddress,
            date = quote.date,
            extraCharges = quote.extraCharges,
            notes = quote.notes,
            products = invoiceProducts,
            total = quote.total
        )

        // Guardar invoice principal
        db.collection("invoices").document(invoiceId).set(invoice)
            .addOnSuccessListener {
                // Guardar los detalles de la invoice
                val detailsRef = db.collection("invoices").document(invoiceId).collection("invoiceDetails")
                val batch = db.batch()

                invoiceProducts.forEachIndexed { index, product ->
                    val docRef = detailsRef.document()
                    val data = hashMapOf(
                        "productId" to product.productId,
                        "productName" to product.name,  // Mantener consistencia con la BD
                        "price" to product.price,
                    )
                    batch.set(docRef, data)
                }

                batch.commit().addOnSuccessListener {
                    Toast.makeText(this, "Quote convertida a Invoice ✅", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al convertir la Quote ❌", Toast.LENGTH_SHORT).show()
            }
    }


    private fun confirmConvert(quote: Quote) {
        val alert = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Convertir a Invoice")
            .setMessage("¿Quieres convertir esta Quote en una Invoice?")
            .setPositiveButton("Sí") { _, _ ->
                convertQuoteToInvoice(quote)
            }
            .setNegativeButton("Cancelar", null)
            .create()
        alert.show()
    }

    private fun getQuotes() {
        val db = FirebaseFirestore.getInstance()
        db.collection("quotes")
            .orderBy("date") // ⚠️ Si es String, ordenará alfabéticamente
            .get()
            .addOnSuccessListener { quotesSnapshot ->

                quoteList.clear()

                for (quoteDoc in quotesSnapshot) {
                    val dateObj = quoteDoc.getTimestamp("date")?.toDate()
                    val quoteId = quoteDoc.id
                    val customerId = quoteDoc.getString("customerId") ?: ""
                    val customerName = quoteDoc.getString("customerName") ?: ""
                    val customerAddress = quoteDoc.getString("customerAddress") ?: ""
                    val timestamp = quoteDoc.getTimestamp("date")
                    val dateFormatted = if (timestamp != null) {
                        val dateObj = timestamp.toDate()
                        val formatter = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale("en", "US"))
                        formatter.timeZone = TimeZone.getTimeZone("America/Mexico_City") // Hora centro México
                        formatter.format(dateObj)
                    } else {
                        ""
                    }

                    val extraCharges = quoteDoc.getDouble("extraCharges") ?: 0.0
                    val notes = quoteDoc.getString("notes") ?: ""
                    val total = quoteDoc.getDouble("total") ?: 0.0

                    db.collection("quotes").document(quoteId)
                        .collection("quoteDetails")
                        .get()
                        .addOnSuccessListener { detailsSnapshot ->
                            val products = detailsSnapshot.map { detailDoc ->
                                ProductDetail(
                                    productId = detailDoc.getString("productId") ?: "",
                                    name = detailDoc.getString("productName") ?: "",
                                    price = detailDoc.getDouble("price") ?: 0.0
                                )
                            }

                            val quote = Quote(
                                id = quoteId,
                                customerId = customerId,
                                customerName = customerName,
                                customerAddress = customerAddress,
                                date = dateFormatted,
                                extraCharges = extraCharges,
                                notes = notes,
                                total = total,
                                products = products
                            )

                            quoteList.add(quote)
                            adapter.notifyDataSetChanged()
                        }
                }
            }
    }

    private fun filterQuotes(text: String) {
        val filteredList = quoteList.filter {
            it.customerName.contains(text, ignoreCase = true) ||
                    it.date.contains(text, ignoreCase = true)
        }
        adapter.updateList(filteredList)
    }

    private fun openEditScreen(quote: Quote) {
        val editQuoteSheet = EditQuoteBottomSheet.newInstance(quote.id)
        editQuoteSheet.setOnQuoteUpdatedListener(object : EditQuoteBottomSheet.OnQuoteUpdatedListener {
            override fun onQuoteUpdated() {
                getQuotes()
            }
        })
        editQuoteSheet.show(supportFragmentManager, "EditQuoteBottomSheet")
    }

    private fun deleteQuote(quote: Quote) {
        val quoteRef = db.collection("quotes").document(quote.id)

        // Primero eliminamos los detalles
        quoteRef.collection("quoteDetails").get().addOnSuccessListener { detailsSnapshot ->
            val batch = db.batch()
            for (detailDoc in detailsSnapshot) {
                batch.delete(detailDoc.reference)
            }
            // Eliminamos el quote principal
            batch.delete(quoteRef)

            batch.commit().addOnSuccessListener {
                quoteList.remove(quote)
                adapter.updateList(quoteList)
                Toast.makeText(this, "Quote deleted successfully", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting quote", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error deleting quote details", Toast.LENGTH_SHORT).show()
        }
    }


    private fun confirmDelete(quote: Quote) {
        val alert = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Quote")
            .setMessage("Are you sure you want to delete this quote?")
            .setPositiveButton("Yes") { _, _ ->
                deleteQuote(quote)
            }
            .setNegativeButton("Cancel", null)
            .create()
        alert.show()
    }
}
