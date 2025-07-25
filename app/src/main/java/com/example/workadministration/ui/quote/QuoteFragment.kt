package com.example.workadministration.ui.quote

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
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

class QuoteFragment : Fragment(), AddCustomerBottomSheet.OnCustomerAddedListener, AddQuoteBottomSheet.OnQuoteSavedListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchQuote: EditText
    private lateinit var adapter: QuoteAdapter
    private val quoteList = mutableListOf<Quote>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCustomerAdded(customer: Customer) {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_quote, container, false)

        val btnAgregar = view.findViewById<ImageButton>(R.id.btnAgregarQuote)
        btnAgregar.setOnClickListener {
            val bottomSheet = AddQuoteBottomSheet(this) // 'this' porque QuoteFragment implementa OnQuoteSavedListener
            bottomSheet.show(parentFragmentManager, "AddQuoteBottomSheet")
        }


        recyclerView = view.findViewById(R.id.recyclerQuotes)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = QuoteAdapter(
            quoteList,
            requireContext(),
            onDeleteClick = { quote -> confirmDelete(quote) },
            onEditClick = { quote -> openEditScreen(quote) },
        )
        recyclerView.adapter = adapter

        searchQuote = view.findViewById(R.id.buscarQuote)
        searchQuote.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterQuotes(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        getQuotes()
        return view
    }

    override fun onQuoteSaved() {
        getQuotes()
    }

    private fun getQuotes() {
        db.collection("quotes")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { quotesSnapshot ->

                quoteList.clear()

                for (quoteDoc in quotesSnapshot) {
                    val quoteId = quoteDoc.id
                    val customerId = quoteDoc.getString("customerId") ?: ""
                    val customerName = quoteDoc.getString("customerName") ?: ""
                    val customerAddress = quoteDoc.getString("customerAddress") ?: ""
                    val timestamp = quoteDoc.getTimestamp("date")
                    val dateFormatted = if (timestamp != null) {
                        val dateObj = timestamp.toDate()
                        val formatter = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale("en", "US"))
                        formatter.timeZone = TimeZone.getTimeZone("America/Mexico_City")
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
        editQuoteSheet.show(parentFragmentManager, "EditQuoteBottomSheet")
    }

    private fun deleteQuote(quote: Quote) {
        db.collection("quotes").document(quote.id)
            .delete()
            .addOnSuccessListener {
                quoteList.remove(quote)
                adapter.updateList(quoteList)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error deleting quote", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDelete(quote: Quote) {
        val alert = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Quote")
            .setMessage("Are you sure you want to delete this quote?")
            .setPositiveButton("Yes") { _, _ -> deleteQuote(quote) }
            .setNegativeButton("Cancel", null)
            .create()
        alert.show()
    }
}
