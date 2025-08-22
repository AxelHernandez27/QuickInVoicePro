package com.example.workadministration.ui.quote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.example.workadministration.ui.customer.AddCustomerBottomSheet
import com.example.workadministration.ui.customer.Customer
import com.example.workadministration.ui.product.Product
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class AddQuoteBottomSheet(private var listener: OnQuoteSavedListener) : BottomSheetDialogFragment(),
    AddCustomerBottomSheet.OnCustomerAddedListener {

    interface OnQuoteSavedListener {
        fun onQuoteSaved()
    }

    private lateinit var customerAdapter: ArrayAdapter<String>

    private lateinit var autoCompleteClient: AutoCompleteTextView
    private lateinit var autoCompleteProduct: AutoCompleteTextView
    private lateinit var tvSubtotalAmount: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var etAdditionalNotes: EditText
    private lateinit var etExtraCharges: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var recyclerViewProducts: RecyclerView

    private val db = FirebaseFirestore.getInstance()
    private var allProducts = listOf<Product>()
    private var allCustomers = listOf<Customer>()
    private val selectedProducts = mutableListOf<Pair<Product, Int>>() // lista para adapter
    private lateinit var adapter: QuoteProductAdapter

    private var selectedCustomer: Customer? = null
    private var subtotal = 0.0
    private var extraCharges = 0.0

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.form_quote_agregar, container, false)

        autoCompleteClient = view.findViewById(R.id.autoCompleteClient)
        autoCompleteProduct = view.findViewById(R.id.autoCompleteProduct)
        tvSubtotalAmount = view.findViewById(R.id.tvSubtotalAmount)
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        etAdditionalNotes = view.findViewById(R.id.etAdditionalNotes)
        etExtraCharges = view.findViewById(R.id.etExtraCharges)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)
        recyclerViewProducts = view.findViewById(R.id.recyclerViewProducts)

        view.findViewById<Button>(R.id.btnAddClient).setOnClickListener {
            val addCustomerBottomSheet = AddCustomerBottomSheet(this)
            addCustomerBottomSheet.show(parentFragmentManager, "AddCustomerBottomSheet")
        }

        view.findViewById<Button>(R.id.btnAddCustomProduct).setOnClickListener {
            showAddCustomProductDialog()
        }

        loadClients()
        loadProducts()

        etExtraCharges.addTextChangedListener {
            extraCharges = it.toString().toDoubleOrNull() ?: 0.0
            updateTotal()
        }

        setupRecyclerView()

        btnSave.setOnClickListener { saveQuote() }
        btnCancel.setOnClickListener { dismiss() }

        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        btnClose.setOnClickListener { dismiss() }

        return view
    }

    private fun setupRecyclerView() {
        adapter = QuoteProductAdapter(
            selectedProducts,
            onQuantityChanged = { _, _ -> updateTotal() },
            onEditCustomProduct = { showEditCustomProductDialog(it) },
            onDeleteProduct = { position ->
                selectedProducts.removeAt(position)
                adapter.notifyItemRemoved(position)
                updateTotal()
            }
        )

        recyclerViewProducts.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewProducts.adapter = adapter

        val itemTouchHelperCallback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.moveItem(vh.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled() = true
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerViewProducts)
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

            autoCompleteClient.setOnClickListener { if (autoCompleteClient.adapter != null) autoCompleteClient.showDropDown() }
            autoCompleteClient.setOnFocusChangeListener { _, hasFocus -> if (hasFocus && autoCompleteClient.adapter != null) autoCompleteClient.showDropDown() }
        }
    }

    override fun onCustomerAdded(customer: Customer) {
        allCustomers += customer
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
            val productAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, productNames)
            autoCompleteProduct.setAdapter(productAdapter)

            autoCompleteProduct.setOnItemClickListener { _, _, position, _ ->
                val name = productAdapter.getItem(position)
                val product = allProducts.find { it.name == name }
                product?.let { addOrUpdateProduct(it) }
                autoCompleteProduct.setText("")
            }

            autoCompleteProduct.setOnClickListener { if (autoCompleteProduct.adapter != null) autoCompleteProduct.showDropDown() }
            autoCompleteProduct.setOnFocusChangeListener { _, hasFocus -> if (hasFocus && autoCompleteProduct.adapter != null) autoCompleteProduct.showDropDown() }
        }
    }

    private fun addOrUpdateProduct(product: Product) {
        val index = selectedProducts.indexOfFirst { it.first.id == product.id }
        if (index != -1) {
            val current = selectedProducts[index]
            selectedProducts[index] = current.first to current.second + 1
            adapter.notifyItemChanged(index)
        } else {
            selectedProducts.add(product to 1)
            adapter.notifyItemInserted(selectedProducts.size - 1)
        }
        updateTotal()
    }

    private fun showAddCustomProductDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_custom_product, null)
        val etName = dialogView.findViewById<EditText>(R.id.etCustomProductName)
        val etPrice = dialogView.findViewById<EditText>(R.id.etCustomProductPrice)

        AlertDialog.Builder(requireContext())
            .setTitle("Custom Product")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val price = etPrice.text.toString().toDoubleOrNull()
                if (name.isNotEmpty() && price != null && price > 0) {
                    val customProduct = Product("custom_${System.currentTimeMillis()}", name, price)
                    addOrUpdateProduct(customProduct)
                } else {
                    Toast.makeText(requireContext(), "Enter a valid name and price", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCustomProductDialog(productId: String) {
        val index = selectedProducts.indexOfFirst { it.first.id == productId }
        if (index == -1) return
        val (product, quantity) = selectedProducts[index]

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
                    selectedProducts[index] = product.copy(name = name, price = price) to quantity
                    adapter.notifyItemChanged(index)
                    updateTotal()
                } else {
                    Toast.makeText(requireContext(), "Enter a valid name and price", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateTotal() {
        subtotal = selectedProducts.sumOf { (product, qty) -> product.price * qty }
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
            selectedProducts.forEachIndexed { position, (product, qty) ->
                val detail = hashMapOf(
                    "productId" to product.id,
                    "name" to product.name,
                    "price" to product.price,
                    "quantity" to qty,
                    "position" to position // <-- guardamos la posición
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


    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
