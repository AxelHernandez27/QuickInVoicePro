package com.example.workadministration.ui.quote

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.example.workadministration.ui.customer.AddCustomerBottomSheet
import com.example.workadministration.ui.customer.Customer
import com.example.workadministration.ui.product.Product
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.UUID

class EditQuoteBottomSheet : BottomSheetDialogFragment(), AddCustomerBottomSheet.OnCustomerAddedListener {

    interface OnQuoteUpdatedListener {
        fun onQuoteUpdated()
    }

    private var listener: OnQuoteUpdatedListener? = null
    fun setOnQuoteUpdatedListener(listener: OnQuoteUpdatedListener) {
        this.listener = listener
    }

    private lateinit var autoCompleteClient: AutoCompleteTextView
    private lateinit var autoCompleteProduct: AutoCompleteTextView
    private lateinit var recyclerViewProducts: RecyclerView
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
    private val selectedProducts = mutableListOf<Pair<Product, Int>>() // producto + cantidad

    private lateinit var adapter: QuoteProductAdapter
    private var selectedCustomer: Customer? = null
    private var subtotal = 0.0
    private var extraCharges = 0.0
    private var quoteId: String? = null

    companion object {
        private const val ARG_QUOTE_ID = "quoteId"

        fun newInstance(quoteId: String): EditQuoteBottomSheet {
            val fragment = EditQuoteBottomSheet()
            val bundle = Bundle()
            bundle.putString(ARG_QUOTE_ID, quoteId)
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quoteId = arguments?.getString(ARG_QUOTE_ID)
        if (quoteId == null) dismiss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.form_quote_editar, container, false)

        autoCompleteClient = view.findViewById(R.id.autoCompleteClient)
        val btnAddClient = view.findViewById<Button>(R.id.btnAddClient)
        autoCompleteProduct = view.findViewById(R.id.autoCompleteProduct)
        recyclerViewProducts = view.findViewById(R.id.recyclerViewProducts)
        tvSubtotalAmount = view.findViewById(R.id.tvSubtotalAmount)
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        etAdditionalNotes = view.findViewById(R.id.etAdditionalNotes)
        etExtraCharges = view.findViewById(R.id.etExtraCharges)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnAddCustomProduct = view.findViewById(R.id.btnAddCustomProduct)

        loadClients {
            loadProducts {
                loadQuoteData()
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

        setupRecyclerView()
        btnAddCustomProduct.setOnClickListener { showAddCustomProductDialog() }
        btnSave.setOnClickListener { updateQuote() }
        btnCancel.setOnClickListener { dismiss() }

        return view
    }

    private fun setupRecyclerView() {
        adapter = QuoteProductAdapter(
            selectedProducts,
            onQuantityChanged = { _, _ -> updateTotal() },
            onEditCustomProduct = { productId ->
                val pair = selectedProducts.find { it.first.id == productId }
                pair?.let { showEditCustomProductDialog(it.first) }
            },
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

    private fun loadQuoteData() {
        quoteId?.let { id ->
            db.collection("quotes").document(id).get().addOnSuccessListener { doc ->
                val customerName = doc.getString("customerName") ?: ""
                autoCompleteClient.setText(customerName)
                selectedCustomer = allCustomers.find { it.fullname == customerName }

                etAdditionalNotes.setText(doc.getString("notes") ?: "")
                extraCharges = doc.getDouble("extraCharges") ?: 0.0
                etExtraCharges.setText(extraCharges.toString())

                db.collection("quotes").document(id).collection("quoteDetails")
                    .orderBy("position")
                    .get().addOnSuccessListener { details ->
                        selectedProducts.clear()
                        details.forEach { detailDoc ->
                            val product = Product(
                                id = detailDoc.getString("productId") ?: "",
                                name = detailDoc.getString("name") ?: "",
                                price = detailDoc.getDouble("price") ?: 0.0
                            )
                            val quantity = detailDoc.getLong("quantity")?.toInt() ?: 1
                            selectedProducts.add(product to quantity)
                        }
                        adapter.notifyDataSetChanged()
                        updateTotal()
                    }
            }
        }
    }

    private fun loadClients(onComplete: () -> Unit) {
        db.collection("customers").get().addOnSuccessListener { documents ->
            allCustomers = documents.map { doc -> doc.toObject(Customer::class.java).copy(id = doc.id) }
            val names = allCustomers.map { it.fullname }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
            autoCompleteClient.setAdapter(adapter)
            autoCompleteClient.setOnItemClickListener { _, _, position, _ ->
                val name = adapter.getItem(position)
                selectedCustomer = allCustomers.find { it.fullname == name }
            }
            autoCompleteClient.setOnClickListener { if (autoCompleteClient.adapter != null) autoCompleteClient.showDropDown() }
            autoCompleteClient.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) autoCompleteClient.showDropDown() }
            onComplete()
        }
    }

    override fun onCustomerAdded(customer: Customer) {
        allCustomers += customer
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allCustomers.map { it.fullname })
        autoCompleteClient.setAdapter(adapter)
        selectedCustomer = customer
        autoCompleteClient.setText(customer.fullname, false)
        autoCompleteClient.error = null
        autoCompleteClient.clearFocus()
        Toast.makeText(requireContext(), "Selected customer: ${customer.fullname}", Toast.LENGTH_SHORT).show()
    }

    private fun loadProducts(onComplete: () -> Unit) {
        db.collection("products").get().addOnSuccessListener { documents ->
            allProducts = documents.map { it.toObject(Product::class.java).copy(id = it.id) }
            val productNames = allProducts.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, productNames)
            autoCompleteProduct.setAdapter(adapter)

            autoCompleteProduct.setOnItemClickListener { _, _, position, _ ->
                val name = adapter.getItem(position)
                val product = allProducts.find { it.name == name }
                product?.let {
                    addOrUpdateProduct(it)
                    autoCompleteProduct.setText("")
                }
            }

            autoCompleteProduct.setOnClickListener { if (autoCompleteProduct.adapter != null) autoCompleteProduct.showDropDown() }
            autoCompleteProduct.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) autoCompleteProduct.showDropDown() }
            onComplete()
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
            .setTitle("Add Custom Product")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val price = etPrice.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty() && price > 0.0) {
                    val product = Product("custom_${UUID.randomUUID()}", name, price)
                    addOrUpdateProduct(product)
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
                    val index = selectedProducts.indexOfFirst { it.first.id == product.id }
                    if (index != -1) {
                        selectedProducts[index] = product.copy(name = name, price = price) to selectedProducts[index].second
                        adapter.notifyItemChanged(index)
                        updateTotal()
                    }
                } else {
                    Toast.makeText(requireContext(), "Invalid data", Toast.LENGTH_SHORT).show()
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

    private fun updateQuote() {
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

        val quoteData = mapOf(
            "customerId" to selectedCustomer!!.id,
            "customerName" to selectedCustomer!!.fullname,
            "customerAddress" to selectedCustomer!!.address,
            "extraCharges" to extraCharges,
            "notes" to notes,
            "total" to total
        )

        quoteId?.let { id ->
            db.collection("quotes").document(id)
                .set(quoteData, SetOptions.merge())
                .addOnSuccessListener {
                    val detailsRef = db.collection("quotes").document(id).collection("quoteDetails")
                    detailsRef.get().addOnSuccessListener { existing ->
                        existing.forEach { it.reference.delete() }
                        selectedProducts.forEachIndexed { position, (product, qty) ->
                            val detail = mapOf(
                                "productId" to product.id,
                                "name" to product.name,
                                "price" to product.price,
                                "quantity" to qty,
                                "position" to position
                            )
                            detailsRef.add(detail)
                        }
                        Toast.makeText(requireContext(), "Quote updated", Toast.LENGTH_SHORT).show()
                        listener?.onQuoteUpdated()
                        dismiss()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error updating quote", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
