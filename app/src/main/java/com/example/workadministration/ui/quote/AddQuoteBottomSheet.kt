package com.example.workadministration.ui.quote

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import com.example.workadministration.R
import com.example.workadministration.ui.customer.AddCustomerBottomSheet
import com.example.workadministration.ui.customer.Customer
import com.example.workadministration.ui.product.Product
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class AddQuoteBottomSheet(private var listener: OnQuoteSavedListener) : BottomSheetDialogFragment(), AddCustomerBottomSheet.OnCustomerAddedListener {

    interface OnQuoteSavedListener {
        fun onQuoteSaved()
    }

    //private lateinit var listener: OnQuoteSavedListener
    private lateinit var customerAdapter: ArrayAdapter<String>

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
    private val productViews = mutableMapOf<String, View>()
    private val selectedProducts = mutableMapOf<String, Pair<Product, Int>>() // ID -> (Product, Quantity)

    private var selectedCustomer: Customer? = null
    private var subtotal = 0.0
    private var extraCharges = 0.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.form_quote_agregar, container, false)

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
        view.findViewById<Button>(R.id.btnAddCustomProduct).setOnClickListener {
            showAddCustomProductDialog()
        }

        loadClients()
        loadProducts()

        btnAddClient.setOnClickListener {
            val addCustomerBottomSheet = AddCustomerBottomSheet(this)
            addCustomerBottomSheet.show(parentFragmentManager, "AddCustomerBottomSheet")
        }

        etExtraCharges.addTextChangedListener {
            extraCharges = it.toString().toDoubleOrNull() ?: 0.0
            updateTotal()
        }

        btnSave.setOnClickListener { saveQuote() }
        btnCancel.setOnClickListener { dismiss() }

        return view
    }

    private fun loadClients() {
        db.collection("customers").get().addOnSuccessListener { documents ->
            allCustomers = documents.map { it.toObject(Customer::class.java).copy(id = it.id) }
            customerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allCustomers.map { it.fullname })
            autoCompleteClient.setAdapter(customerAdapter)

            autoCompleteClient.setOnItemClickListener { _, _, position, _ ->
                val name = customerAdapter.getItem(position)
                selectedCustomer = allCustomers.find { it.fullname == name }
                autoCompleteClient.error = null
            }

            autoCompleteClient.setOnClickListener {
                if (autoCompleteClient.adapter != null) autoCompleteClient.showDropDown()
            }

            autoCompleteClient.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && autoCompleteClient.adapter != null) autoCompleteClient.showDropDown()
            }
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

    private fun loadProducts() {
        db.collection("products").get().addOnSuccessListener { documents ->
            allProducts = documents.map { it.toObject(Product::class.java).copy(id = it.id) }
            val productNames = allProducts.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, productNames)
            autoCompleteProduct.setAdapter(adapter)
            adapter.notifyDataSetChanged()

            autoCompleteProduct.setOnItemClickListener { _, _, position, _ ->
                val name = adapter.getItem(position)
                val product = allProducts.find { it.name == name }
                product?.let {
                    if (selectedProducts.containsKey(it.id)) {
                        incrementQuantity(it.id)
                    } else {
                        selectedProducts[it.id] = Pair(it, 1)
                        addProductView(it)
                    }
                    updateTotal()
                    autoCompleteProduct.setText("")
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
        val btnIncrease = view.findViewById<Button>(R.id.btnIncreaseQuantity)
        val btnDecrease = view.findViewById<Button>(R.id.btnDecreaseQuantity)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteProduct)
        val btnEdit = view.findViewById<ImageButton>(R.id.btnEditProduct)

        etQuantity.setText("1")
        etQuantity.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        selectedProducts[product.id] = product to 1

        btnIncrease.setOnClickListener {
            val currentQty = etQuantity.text.toString().toIntOrNull() ?: 1
            val newQty = currentQty + 1
            etQuantity.setText(newQty.toString())
            selectedProducts[product.id] = product to newQty
            updateTotal()
        }

        btnDecrease.setOnClickListener {
            val currentQty = etQuantity.text.toString().toIntOrNull() ?: 1
            if (currentQty > 1) {
                val newQty = currentQty - 1
                etQuantity.setText(newQty.toString())
                selectedProducts[product.id] = product to newQty
                etQuantity.error = null
            } else {
                etQuantity.setText("1")
                etQuantity.error = "Min. 1"
            }
            updateTotal()
        }

        etQuantity.addTextChangedListener {
            val qty = it.toString().toIntOrNull()
            if (qty != null && qty >= 1) {
                selectedProducts[product.id] = product to qty
                etQuantity.error = null
            } else {
                etQuantity.setText("1")
                selectedProducts[product.id] = product to 1
                etQuantity.error = "Min 1"
            }
            updateTotal()
        }

        btnDelete.setOnClickListener {
            selectedProducts.remove(product.id)
            layoutProductsContainer.removeView(view)
            updateTotal()
        }

        if (product.id.startsWith("custom_")) {
            btnEdit.visibility = View.VISIBLE
            btnEdit.setOnClickListener {
                showEditCustomProductDialog(product.id)
            }
        } else {
            btnEdit.visibility = View.GONE
        }

        productViews[product.id] = view
        layoutProductsContainer.addView(view)
    }

    private fun showEditCustomProductDialog(productId: String) {
        val (product, quantity) = selectedProducts[productId] ?: return

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_custom_product, null)
        val etName = dialogView.findViewById<EditText>(R.id.etCustomProductName)
        val etPrice = dialogView.findViewById<EditText>(R.id.etCustomProductPrice)

        etName.setText(product.name)
        etPrice.setText(product.price.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Custom Product")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = etName.text.toString().trim()
                val price = etPrice.text.toString().toDoubleOrNull()

                if (name.isNotEmpty() && price != null && price > 0) {
                    val updatedProduct = product.copy(name = name, price = price)
                    selectedProducts[productId] = Pair(updatedProduct, quantity)
                    refreshProductListLayout()
                    updateTotal()
                } else {
                    Toast.makeText(requireContext(), "Enter a valid name and price", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshProductListLayout() {
        layoutProductsContainer.removeAllViews()
        selectedProducts.forEach { (_, pair) ->
            val (product, _) = pair
            addProductView(product)
        }
    }

    private fun incrementQuantity(productId: String) {
        val pair = selectedProducts[productId]
        if (pair != null) {
            val newQty = pair.second + 1
            selectedProducts[productId] = pair.first to newQty
            productViews[productId]?.findViewById<EditText>(R.id.EtProductQuantity)?.setText(newQty.toString())
        }
    }

    private fun updateTotal() {
        subtotal = selectedProducts.values.sumOf { (product, quantity) ->
            product.price * quantity
        }
        val total = subtotal + extraCharges
        tvSubtotalAmount.text = "$%.2f".format(subtotal)
        tvTotalAmount.text = "$%.2f".format(total)
    }

    private fun saveQuote() {
        val clientName = autoCompleteClient.text.toString().trim()
        if (clientName.isEmpty() || selectedCustomer == null) {
            autoCompleteClient.error = "Please select a client"
            return
        }

        if (selectedProducts.isEmpty()) {
            Toast.makeText(requireContext(), "Please add at least one product", Toast.LENGTH_SHORT).show()
            return
        }

        val notes = etAdditionalNotes.text.toString().trim()
        val total = subtotal + extraCharges
        val quoteRef = db.collection("quotes").document()

        val quoteData = hashMapOf(
            "id" to quoteRef.id,
            "customerId" to selectedCustomer!!.id,
            "customerName" to selectedCustomer!!.fullname,
            "customerAddress" to selectedCustomer!!.address,
            "date" to Timestamp.now(),
            "extraCharges" to extraCharges,
            "notes" to notes,
            "total" to total
        )

        quoteRef.set(quoteData).addOnSuccessListener {
            val detailsCollection = quoteRef.collection("quoteDetails")
            selectedProducts.values.forEach { (product, quantity) ->
                val detail = hashMapOf(
                    "productId" to product.id,
                    "name" to product.name,
                    "price" to product.price,
                    "quantity" to quantity
                )
                detailsCollection.add(detail)
            }

            Toast.makeText(requireContext(), "Quote saved successfully", Toast.LENGTH_SHORT).show()
            listener.onQuoteSaved()
            dismiss()
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Error saving quote", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addOrUpdateProduct(product: Product) {
        val existingProduct = selectedProducts[product.id]
        if (existingProduct != null) {
            incrementQuantity(product.id)
        } else {
            selectedProducts[product.id] = Pair(product, 1)
            addProductView(product)
        }
        updateTotal()
    }

    private fun showAddCustomProductDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_custom_product, null)
        val etName = dialogView.findViewById<EditText>(R.id.etCustomProductName)
        val etPrice = dialogView.findViewById<EditText>(R.id.etCustomProductPrice)

        AlertDialog.Builder(requireContext())
            .setTitle("Customized Product")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val price = etPrice.text.toString().toDoubleOrNull()

                if (name.isNotEmpty() && price != null && price > 0) {
                    val customProduct = Product(id = "custom_${System.currentTimeMillis()}", name = name, price = price)
                    addOrUpdateProduct(customProduct)
                } else {
                    Toast.makeText(requireContext(), "Enter a valid name and price", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
