package com.example.workadministration.ui.invoice

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.example.workadministration.R
import com.example.workadministration.ui.customer.AddCustomerBottomSheet
import com.example.workadministration.ui.customer.Customer
import com.example.workadministration.ui.product.Product
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.UUID

class EditInvoiceBottomSheet : BottomSheetDialogFragment(), AddCustomerBottomSheet.OnCustomerAddedListener {

    interface OnInvoiceUpdatedListener {
        fun onInvoiceUpdated()
    }

    private var listener: OnInvoiceUpdatedListener? = null
    private lateinit var customerAdapter: ArrayAdapter<String>

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
    private lateinit var btnAddCustomProduct: Button

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
        val btnAddClient = view.findViewById<Button>(R.id.btnAddClient)
        autoCompleteProduct = view.findViewById(R.id.autoCompleteProduct)
        layoutProductsContainer = view.findViewById(R.id.layoutProductsContainer)
        tvSubtotalAmount = view.findViewById(R.id.tvSubtotalAmount)
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        etAdditionalNotes = view.findViewById(R.id.etAdditionalNotes)
        etExtraCharges = view.findViewById(R.id.etExtraCharges)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnAddCustomProduct = view.findViewById(R.id.btnAddCustomProduct)

        loadClients {
            loadProducts {
                loadInvoiceData()
            }
        }

        btnAddClient.setOnClickListener {
            val addCustomerBottomSheet = AddCustomerBottomSheet(this)
            addCustomerBottomSheet.show(parentFragmentManager, "AddCustomerBottomSheet")
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

        btnAddCustomProduct.setOnClickListener { showAddCustomProductDialog() }

        return view
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
                            val quantity = detailDoc.getLong("quantity")?.toInt() ?: 1

                            val isCustom = product.id.isEmpty()
                            selectedProducts.add(product)
                            addProductView(product, quantity, isCustom)
                        }
                        updateTotal()
                    }
            }
        }
    }

    private fun loadClients(onComplete: () -> Unit) {
        db.collection("customers").get().addOnSuccessListener { documents ->
            allCustomers = documents.map { doc ->
                doc.toObject(Customer::class.java).copy(id = doc.id)
            }
            val names = allCustomers.map { it.fullname }
            customerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
            autoCompleteClient.setAdapter(customerAdapter)

            autoCompleteClient.setOnItemClickListener { _, _, position, _ ->
                val name = customerAdapter.getItem(position)
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

    override fun onCustomerAdded(customer: Customer) {
        allCustomers = allCustomers + customer
        customerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allCustomers.map { it.fullname })
        autoCompleteClient.setAdapter(customerAdapter)

        selectedCustomer = customer
        autoCompleteClient.setText(customer.fullname, false)
        autoCompleteClient.error = null
        autoCompleteClient.clearFocus()

        Toast.makeText(requireContext(), "Selected customer: ${customer.fullname}", Toast.LENGTH_SHORT).show()
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
                    refreshProductListLayout()
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

    private fun showAddCustomProductDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_custom_product, null)
        val etName = dialogView.findViewById<EditText>(R.id.etCustomProductName)
        val etPrice = dialogView.findViewById<EditText>(R.id.etCustomProductPrice)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Custom Product")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val price = etPrice.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty() && price > 0.0) {
                    val product = Product("custom_${UUID.randomUUID()}", name, price)
                    selectedProducts.add(product)
                    refreshProductListLayout()
                    updateTotal()
                } else {
                    Toast.makeText(requireContext(), "Invalid name or price", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCustomProductDialog(product: Product) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_custom_product, null)
        val etName = dialogView.findViewById<EditText>(R.id.etCustomProductName)
        val etPrice = dialogView.findViewById<EditText>(R.id.etCustomProductPrice)

        etName.setText(product.name)
        etPrice.setText(product.price.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Custom Product")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val price = etPrice.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty() && price > 0.0) {
                    product.name = name
                    product.price = price
                    refreshProductListLayout()
                    updateTotal()
                } else {
                    Toast.makeText(requireContext(), "Invalid data", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshProductListLayout() {
        layoutProductsContainer.removeAllViews()
        selectedProducts.forEach {
            val quantity = 0
            val isCustom = false
            addProductView(it, quantity, isCustom)
        }
    }

    private fun addProductView(product: Product, quantity: Int, isCustom: Boolean) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.item_invoice_product, layoutProductsContainer, false)
        view.findViewById<TextView>(R.id.tvProductName).text = product.name
        view.findViewById<TextView>(R.id.tvProductPrice).text = "$%.2f".format(product.price)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteProduct)
        val btnEdit = view.findViewById<ImageButton>(R.id.btnEditProduct)

        btnDelete.setOnClickListener {
            selectedProducts.remove(product)
            layoutProductsContainer.removeView(view)
            updateTotal()
        }

        if (product.id.startsWith("custom_")) {
            btnEdit.visibility = View.VISIBLE
            btnEdit.setOnClickListener {
                showEditCustomProductDialog(product)
            }
        } else {
            btnEdit.visibility = View.GONE
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
                                "name" to product.name,
                                "price" to product.price
                            )
                            detailsRef.add(detail)
                        }
                        Toast.makeText(requireContext(), "Invoice updated", Toast.LENGTH_SHORT).show()
                        listener?.onInvoiceUpdated()
                        dismiss()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error updating invoice", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
