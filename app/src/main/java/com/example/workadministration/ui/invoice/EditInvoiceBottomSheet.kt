package com.example.workadministration.ui.invoice

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.workadministration.R
import com.example.workadministration.ui.customer.Customer
import com.example.workadministration.ui.product.Product
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class EditInvoiceBottomSheet : BottomSheetDialogFragment() {

    interface OnInvoiceUpdatedListener {
        fun onInvoiceUpdated()
    }

    private var listener: OnInvoiceUpdatedListener? = null

    fun setOnInvoiceUpdatedListener(listener: OnInvoiceUpdatedListener) {
        this.listener = listener
    }

    private lateinit var autoCompleteClient: AutoCompleteTextView
    private lateinit var autoCompleteProduct: AutoCompleteTextView
    private lateinit var layoutProductsContainer: LinearLayout
    private lateinit var tvSubtotalAmount: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var etAdditionalNotes: EditText
    private lateinit var etExtraCharges: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private val db = FirebaseFirestore.getInstance()
    private var allProducts = listOf<Product>()
    private var allCustomers = listOf<Customer>()
    private val selectedProducts = mutableListOf<Product>()

    private var selectedCustomer: Customer? = null
    private var subtotal = 0.0
    private var extraCharges = 0.0

    private var invoiceId: String? = null

    companion object {
        private const val ARG_INVOICE_ID = "invoiceId"

        fun newInstance(invoiceId: String): EditInvoiceBottomSheet {
            val fragment = EditInvoiceBottomSheet()
            val bundle = Bundle()
            bundle.putString(ARG_INVOICE_ID, invoiceId)
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        invoiceId = arguments?.getString(ARG_INVOICE_ID)
        if (invoiceId == null) {
            dismiss()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.form_ticket_editar, container, false)

        autoCompleteClient = view.findViewById(R.id.autoCompleteClient)
        autoCompleteProduct = view.findViewById(R.id.autoCompleteProduct)
        layoutProductsContainer = view.findViewById(R.id.layoutProductsContainer)
        tvSubtotalAmount = view.findViewById(R.id.tvSubtotalAmount)
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        etAdditionalNotes = view.findViewById(R.id.etAdditionalNotes)
        etExtraCharges = view.findViewById(R.id.etExtraCharges)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)

        loadClients() {
            loadProducts() {
                loadInvoiceData()
            }
        }


        etExtraCharges.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                extraCharges = s.toString().toDoubleOrNull() ?: 0.0
                updateTotal()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSave.setOnClickListener { updateInvoice() }
        btnCancel.setOnClickListener { dismiss() }

        return view
    }

    private fun loadClients(onComplete: () -> Unit) {
        db.collection("customers").get().addOnSuccessListener { documents ->
            allCustomers = documents.map { doc ->
                doc.toObject(Customer::class.java).copy(id = doc.id)
            }
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
                if (hasFocus) autoCompleteClient.showDropDown()
            }

            onComplete()
        }
    }

    private fun loadProducts(onComplete: () -> Unit) {
        db.collection("products").get().addOnSuccessListener { documents ->
            allProducts = documents.map { doc ->
                doc.toObject(Product::class.java).copy(id = doc.id)
            }
            val productNames = allProducts.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, productNames)
            autoCompleteProduct.setAdapter(adapter)

            autoCompleteProduct.setOnItemClickListener { _, _, position, _ ->
                val name = adapter.getItem(position)
                val product = allProducts.find { it.name == name }
                product?.let {
                    selectedProducts.add(it)
                    addProductView(it)
                    updateTotal()
                    autoCompleteProduct.setText("")
                }
            }

            autoCompleteProduct.setOnClickListener {
                if (autoCompleteProduct.adapter != null) autoCompleteProduct.showDropDown()
            }
            autoCompleteProduct.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) autoCompleteProduct.showDropDown()
            }

            onComplete()
        }
    }


    private fun loadInvoiceData() {
        invoiceId?.let { id ->
            db.collection("invoices").document(id).get().addOnSuccessListener { doc ->
                val customerName = doc.getString("customerName") ?: ""
                autoCompleteClient.setText(customerName)
                selectedCustomer = allCustomers.find { it.fullname == customerName }

                etAdditionalNotes.setText(doc.getString("notes") ?: "")
                extraCharges = doc.getDouble("extraCharges") ?: 0.0
                etExtraCharges.setText(extraCharges.toString())

                db.collection("invoices").document(id).collection("invoiceDetails")
                    .get().addOnSuccessListener { details ->
                        details.forEach { detailDoc ->
                            val product = Product(
                                id = detailDoc.getString("productId") ?: "",
                                name = detailDoc.getString("name") ?: "",
                                price = detailDoc.getDouble("price") ?: 0.0
                            )
                            selectedProducts.add(product)
                            addProductView(product)
                        }
                        updateTotal()
                    }
            }
        }
    }

    private fun addProductView(product: Product) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.item_invoice_product, layoutProductsContainer, false)
        view.findViewById<TextView>(R.id.tvProductName).text = product.name
        view.findViewById<TextView>(R.id.tvProductPrice).text = "$%.2f".format(product.price)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteProduct)

        btnDelete.setOnClickListener {
            selectedProducts.remove(product)
            layoutProductsContainer.removeView(view)
            updateTotal()
        }

        layoutProductsContainer.addView(view)
    }

    private fun updateTotal() {
        subtotal = selectedProducts.sumOf { it.price }
        val total = subtotal + extraCharges
        tvSubtotalAmount.text = "$%.2f".format(subtotal)
        tvTotalAmount.text = "$%.2f".format(total)
    }

    private fun updateInvoice() {
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

        val invoiceData = mapOf(
            "customerId" to selectedCustomer!!.id,
            "customerName" to selectedCustomer!!.fullname,
            "customerAddress" to selectedCustomer!!.address,
            "extraCharges" to extraCharges,
            "notes" to notes,
            "total" to total
        )

        invoiceId?.let { id ->
            db.collection("invoices").document(id)
                .set(invoiceData, SetOptions.merge())
                .addOnSuccessListener {
                    val detailsRef = db.collection("invoices").document(id).collection("invoiceDetails")
                    detailsRef.get().addOnSuccessListener { existing ->
                        existing.forEach { it.reference.delete() }
                        selectedProducts.forEach { product ->
                            val detail = mapOf(
                                "productId" to product.id,
                                "name" to product.name,   // CORREGIDO: usar productName consistente
                                "price" to product.price
                            )
                            detailsRef.add(detail)
                        }
                        Toast.makeText(requireContext(), "Invoice updated", Toast.LENGTH_SHORT).show()
                        listener?.onInvoiceUpdated()  // Avisar Activity que se actualizó
                        dismiss()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error updating invoice", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
