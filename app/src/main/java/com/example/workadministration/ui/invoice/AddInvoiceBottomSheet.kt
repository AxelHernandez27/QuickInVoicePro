package com.example.workadministration.ui.invoice

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.widget.addTextChangedListener
import com.example.workadministration.R
import com.example.workadministration.ui.customer.Customer
import com.example.workadministration.ui.product.Product
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class AddInvoiceBottomSheet : BottomSheetDialogFragment() {

    interface OnInvoiceSavedListener {
        fun onInvoiceSaved()
    }

    private lateinit var listener: OnInvoiceSavedListener

    private lateinit var autoCompleteClient: AutoCompleteTextView
    private lateinit var autoCompleteProduct: AutoCompleteTextView
    private lateinit var layoutProductsContainer: LinearLayout
    private lateinit var tvSubtotalAmount: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var etAdditionalNotes: EditText
    private lateinit var etExtraCharges: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private val productViews = mutableMapOf<Product, View>()

    private val db = FirebaseFirestore.getInstance()
    private var allProducts = listOf<Product>()
    private var allCustomers = listOf<Customer>()
    private val selectedProducts = mutableListOf<Product>()

    private var selectedCustomer: Customer? = null
    private var subtotal = 0.0
    private var extraCharges = 0.0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnInvoiceSavedListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnInvoiceSavedListener")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.form_ticket_agregar, container, false)

        autoCompleteClient = view.findViewById(R.id.autoCompleteClient)
        autoCompleteProduct = view.findViewById(R.id.autoCompleteProduct)
        layoutProductsContainer = view.findViewById(R.id.layoutProductsContainer)
        tvSubtotalAmount = view.findViewById(R.id.tvSubtotalAmount)
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        etAdditionalNotes = view.findViewById(R.id.etAdditionalNotes)
        etExtraCharges = view.findViewById(R.id.etExtraCharges)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)

        loadClients()
        loadProducts()

        etExtraCharges.addTextChangedListener {
            extraCharges = it.toString().toDoubleOrNull() ?: 0.0
            updateTotal()
        }

        btnSave.setOnClickListener { saveInvoice() }
        btnCancel.setOnClickListener { dismiss() }

        return view
    }

    private fun loadClients() {
        db.collection("customers").get()
            .addOnSuccessListener { documents ->
                allCustomers = documents.map { it.toObject(Customer::class.java).copy(id = it.id) }
                val names = allCustomers.map { it.fullname }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
                autoCompleteClient.setAdapter(adapter)

                autoCompleteClient.setOnItemClickListener { _, _, position, _ ->
                    val name = adapter.getItem(position)
                    selectedCustomer = allCustomers.find { it.fullname == name }
                }

                autoCompleteClient.setOnClickListener {
                    if (autoCompleteClient.adapter != null) autoCompleteClient.showDropDown()
                }
                autoCompleteClient.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && autoCompleteClient.adapter != null) autoCompleteClient.showDropDown()
                }
            }
    }

    private fun loadProducts() {
        db.collection("products").get()
            .addOnSuccessListener { documents ->
                allProducts = documents.map { it.toObject(Product::class.java).copy(id = it.id) }
                val productNames = allProducts.map { it.name }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, productNames)
                autoCompleteProduct.setAdapter(adapter)

                autoCompleteProduct.setOnItemClickListener { _, _, position, _ ->
                    val name = adapter.getItem(position)
                    val product = allProducts.find { it.name == name }
                    product?.let {
                        if (!selectedProducts.any { p -> p.id == it.id }) {
                            selectedProducts.add(it)
                            addProductView(it)
                            updateTotal()
                            autoCompleteProduct.setText("")
                        } else {
                            Toast.makeText(requireContext(), "Producto ya agregado", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                autoCompleteProduct.setOnClickListener {
                    if (autoCompleteProduct.adapter != null) autoCompleteProduct.showDropDown()
                }
                autoCompleteProduct.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && autoCompleteProduct.adapter != null) autoCompleteProduct.showDropDown()
                }
            }
    }

    private fun addProductView(product: Product) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.item_invoice_product, layoutProductsContainer, false)
        view.findViewById<TextView>(R.id.tvProductName).text = product.name
        view.findViewById<TextView>(R.id.tvProductPrice).text = "$%.2f".format(product.price)
        val etQuantity = view.findViewById<EditText>(R.id.EtProductQuantity)
        etQuantity.setText("1")
        etQuantity.addTextChangedListener {
            updateTotal()
        }

        productViews[product] = view // Guarda la vista asociada al producto

        val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteProduct)

        btnDelete.setOnClickListener {
            selectedProducts.remove(product)
            layoutProductsContainer.removeView(view)
            updateTotal()
        }

        layoutProductsContainer.addView(view)
    }

    private fun updateTotal() {
        subtotal = selectedProducts.sumOf { product ->
            val view = productViews[product]
            val quantity = view?.findViewById<EditText>(R.id.EtProductQuantity)?.text?.toString()?.toIntOrNull() ?: 1
            product.price * quantity
        }
        val total = subtotal + extraCharges
        tvSubtotalAmount.text = "$%.2f".format(subtotal)
        tvTotalAmount.text = "$%.2f".format(total)
    }

    private fun saveInvoice() {
        if (selectedCustomer == null) {
            Toast.makeText(requireContext(), "Please select a client", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedProducts.isEmpty()) {
            Toast.makeText(requireContext(), "Please add at least one product", Toast.LENGTH_SHORT).show()
            return
        }

        val notes = etAdditionalNotes.text.toString().trim()
        val total = subtotal + extraCharges
        val invoiceRef = db.collection("invoices").document()

        val invoiceData = hashMapOf(
            "id" to invoiceRef.id,
            "customerId" to selectedCustomer!!.id,
            "customerName" to selectedCustomer!!.fullname,
            "customerAddress" to selectedCustomer!!.address,
            "date" to Timestamp.now(),
            "extraCharges" to extraCharges,
            "notes" to notes,
            "total" to total
        )

        invoiceRef.set(invoiceData)
            .addOnSuccessListener {
                val detailsCollection = invoiceRef.collection("invoiceDetails")
                selectedProducts.forEach { product ->
                    val productView = productViews[product]
                    val etQuantity = productView?.findViewById<EditText>(R.id.EtProductQuantity)
                    val quantity = etQuantity?.text?.toString()?.toIntOrNull() ?: 1

                    val detail = hashMapOf(
                        "productId" to product.id,
                        "name" to product.name,
                        "price" to product.price,
                        "quantity" to quantity

                    )
                    detailsCollection.add(detail)
                }
                Toast.makeText(requireContext(), "Invoice saved successfully", Toast.LENGTH_SHORT).show()
                listener.onInvoiceSaved()
                dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error saving invoice", Toast.LENGTH_SHORT).show()
            }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
