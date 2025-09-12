package com.example.workadministration.ui.invoice

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.doOnTextChanged
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
import java.util.Calendar
import java.util.Collections
import java.util.Date
import kotlin.Triple

class AddInvoiceBottomSheet : BottomSheetDialogFragment(), AddCustomerBottomSheet.OnCustomerAddedListener {

    interface OnInvoiceSavedListener {
        fun onInvoiceSaved()
    }

    private lateinit var recyclerViewProducts: RecyclerView
    private lateinit var invoiceProductAdapter: InvoiceProductAdapter
    private val selectedProductsList = mutableListOf<Triple<Product, Int, Double>>() // (producto, cantidad, purchasePrice)
    private lateinit var listener: OnInvoiceSavedListener
    private lateinit var customerAdapter: ArrayAdapter<String>

    private lateinit var autoCompleteClient: AutoCompleteTextView
    private lateinit var autoCompleteProduct: AutoCompleteTextView
    private lateinit var tvSubtotalAmount: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var etAdditionalNotes: EditText
    private lateinit var etExtraCharges: EditText
    private lateinit var etPurchasePriceTotal: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private val db = FirebaseFirestore.getInstance()
    private var allProducts = listOf<Product>()
    private var allCustomers = listOf<Customer>()

    private var selectedCustomer: Customer? = null
    private var subtotal = 0.0
    private var extraCharges = 0.0
    private var totalPurchasePrice = 0.0

    private lateinit var purchasePriceWatcher: TextWatcher

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnInvoiceSavedListener) listener = context
        else throw RuntimeException("$context must implement OnInvoiceSavedListener")
    }

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
        val view = inflater.inflate(R.layout.form_ticket_agregar, container, false)

        autoCompleteClient = view.findViewById(R.id.autoCompleteClient)
        val btnAddClient = view.findViewById<Button>(R.id.btnAddClient)
        autoCompleteProduct = view.findViewById(R.id.autoCompleteProduct)
        recyclerViewProducts = view.findViewById(R.id.recyclerViewProducts)
        tvSubtotalAmount = view.findViewById(R.id.tvSubtotalAmount)
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        etAdditionalNotes = view.findViewById(R.id.etAdditionalNotes)
        etExtraCharges = view.findViewById(R.id.etExtraCharges)
        etPurchasePriceTotal = view.findViewById(R.id.etPurchasePriceTotal)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)
        view.findViewById<Button>(R.id.btnAddCustomProduct).setOnClickListener { showAddCustomProductDialog() }

        loadClients()
        loadProducts()

        btnAddClient.setOnClickListener {
            val addCustomerBottomSheet = AddCustomerBottomSheet(this)
            addCustomerBottomSheet.show(parentFragmentManager, "AddCustomerBottomSheet")
        }

        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        btnClose.setOnClickListener { dismiss() }

        etExtraCharges.doOnTextChanged { text, _, _, _ ->
            extraCharges = text.toString().toDoubleOrNull() ?: 0.0
            updateTotal()
        }

        purchasePriceWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val newTotal = s.toString().toDoubleOrNull() ?: 0.0
                if (newTotal != totalPurchasePrice) totalPurchasePrice = newTotal
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        etPurchasePriceTotal.addTextChangedListener(purchasePriceWatcher)

        recyclerViewProducts.layoutManager = LinearLayoutManager(requireContext())
        invoiceProductAdapter = InvoiceProductAdapter(
            selectedProductsList,
            onQuantityChanged = { _, _ -> updateTotal() },
            onPurchasePriceChanged = { position, newPrice ->
                val (product, qty, _) = selectedProductsList[position]
                selectedProductsList[position] = Triple(product, qty, newPrice)
                updateTotal()
            },
            onEditCustomProduct = { productId -> showEditCustomProductDialog(productId) },
            onDeleteProduct = { position ->
                selectedProductsList.removeAt(position)
                invoiceProductAdapter.notifyItemRemoved(position)
                updateTotal()
            }
        )
        recyclerViewProducts.adapter = invoiceProductAdapter

        setupDragAndDrop()

        btnSave.setOnClickListener { saveInvoice() }
        btnCancel.setOnClickListener { dismiss() }

        return view
    }

    private fun loadClients() {
        db.collection("customers").get().addOnSuccessListener { documents ->
            allCustomers = documents.map { it.toObject(Customer::class.java).copy(id = it.id) }
            val names = allCustomers.map { it.fullname }

            customerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
            autoCompleteClient.setAdapter(customerAdapter)
            autoCompleteClient.threshold = 1

            autoCompleteClient.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) autoCompleteClient.showDropDown() }
            autoCompleteClient.setOnClickListener { autoCompleteClient.showDropDown() }

            autoCompleteClient.setOnItemClickListener { _, _, position, _ ->
                val name = customerAdapter.getItem(position)
                selectedCustomer = allCustomers.find { it.fullname == name }
                autoCompleteClient.error = null
            }
        }
    }

    private fun loadProducts() {
        db.collection("products").get().addOnSuccessListener { documents ->
            allProducts = documents.map { it.toObject(Product::class.java).copy(id = it.id) }
            val productNames = allProducts.map { it.name }
            val productAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, productNames)
            autoCompleteProduct.setAdapter(productAdapter)
            autoCompleteProduct.threshold = 1

            autoCompleteProduct.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) autoCompleteProduct.showDropDown() }
            autoCompleteProduct.setOnClickListener { autoCompleteProduct.showDropDown() }

            autoCompleteProduct.setOnItemClickListener { _, _, position, _ ->
                val name = productAdapter.getItem(position)
                val product = allProducts.find { it.name == name }
                product?.let {
                    val existingIndex = selectedProductsList.indexOfFirst { it.first.id == product.id }
                    if (existingIndex != -1) {
                        val (prod, qty, purchasePrice) = selectedProductsList[existingIndex]
                        selectedProductsList[existingIndex] = Triple(prod, qty + 1, purchasePrice)
                        invoiceProductAdapter.notifyItemChanged(existingIndex)
                    } else {
                        selectedProductsList.add(Triple(product, 1, 0.0))
                        invoiceProductAdapter.notifyItemInserted(selectedProductsList.size - 1)
                    }
                    updateTotal()
                    autoCompleteProduct.setText("")
                }
            }

        }
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
                    val index = selectedProductsList.indexOfFirst { it.first.id == customProduct.id }
                    if (index == -1) {
                        selectedProductsList.add(Triple(customProduct, 1, 0.0))
                        invoiceProductAdapter.notifyItemInserted(selectedProductsList.size - 1)
                        updateTotal()
                    }
                } else {
                    Toast.makeText(requireContext(), "Enter a valid name and price", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCustomProductDialog(productId: String) {
        val index = selectedProductsList.indexOfFirst { it.first.id == productId }
        if (index == -1) return

        val (product, quantity, purchasePrice) = selectedProductsList[index]

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_custom_product, null)
        val etName = dialogView.findViewById<EditText>(R.id.etCustomProductName)
        val etPrice = dialogView.findViewById<EditText>(R.id.etCustomProductPrice)

        etName.setText(product.name)
        etPrice.setText(product.price.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Customized Product")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                val newPrice = etPrice.text.toString().toDoubleOrNull()
                if (newName.isNotEmpty() && newPrice != null && newPrice > 0) {
                    val updatedProduct = product.copy(name = newName, price = newPrice)
                    selectedProductsList[index] = Triple(updatedProduct, quantity, purchasePrice)
                    invoiceProductAdapter.notifyItemChanged(index)
                    updateTotal()
                } else {
                    Toast.makeText(requireContext(), "Enter a valid name and price", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateTotal() {
        subtotal = selectedProductsList.sumOf { (product, qty, _) -> product.price * qty }
        totalPurchasePrice = selectedProductsList.sumOf { (_, qty, purchasePrice) -> purchasePrice * qty }
        val total = subtotal + extraCharges

        tvSubtotalAmount.text = "$%.2f".format(subtotal)
        tvTotalAmount.text = "$%.2f".format(total)

        etPurchasePriceTotal.removeTextChangedListener(purchasePriceWatcher)
        etPurchasePriceTotal.setText("%.2f".format(totalPurchasePrice))
        etPurchasePriceTotal.addTextChangedListener(purchasePriceWatcher)
    }

    private fun saveInvoice() {
        val clientName = autoCompleteClient.text.toString().trim()
        if (clientName.isEmpty() || selectedCustomer == null) {
            autoCompleteClient.error = "Please select a client"
            return
        }
        if (selectedProductsList.isEmpty()) {
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
            "subtotal" to subtotal,
            "totalPurchasePrice" to totalPurchasePrice,
            "total" to total
        )

        invoiceRef.set(invoiceData).addOnSuccessListener {
            val detailsCollection = invoiceRef.collection("invoiceDetails")
            selectedProductsList.forEachIndexed { index, (product, quantity, purchasePrice) ->
                val detail = hashMapOf(
                    "productId" to product.id,
                    "name" to product.name,
                    "price" to product.price,
                    "purchasePrice" to purchasePrice,
                    "quantity" to quantity,
                    "position" to index
                )
                detailsCollection.add(detail)
            }
            updateMonthlyReport(invoiceRef.id, total, totalPurchasePrice, Date())
            Toast.makeText(requireContext(), "Invoice saved successfully", Toast.LENGTH_SHORT).show()
            listener.onInvoiceSaved()
            dismiss()
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Error saving invoice", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateMonthlyReport(invoiceId: String, invoiceTotal: Double, materialsTotal: Double, invoiceDate: Date) {
        val calendar = Calendar.getInstance().apply { time = invoiceDate }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val reportId = "${year}_$month"
        val reportRef = db.collection("reports").document(reportId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(reportRef)
            val currentTotalTickets = snapshot.getDouble("totalTickets") ?: 0.0
            val currentTotalMaterials = snapshot.getDouble("totalMaterials") ?: 0.0
            val currentProfit = snapshot.getDouble("profit") ?: 0.0
            val newData = mapOf(
                "year" to year,
                "month" to month,
                "totalTickets" to currentTotalTickets + invoiceTotal,
                "totalMaterials" to currentTotalMaterials + materialsTotal,
                "profit" to currentProfit + (invoiceTotal - materialsTotal)
            )
            transaction.set(reportRef, newData)
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

    private fun setupDragAndDrop() {
        val itemTouchHelperCallback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                Collections.swap(selectedProductsList, from, to)
                invoiceProductAdapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = true
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerViewProducts)
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
