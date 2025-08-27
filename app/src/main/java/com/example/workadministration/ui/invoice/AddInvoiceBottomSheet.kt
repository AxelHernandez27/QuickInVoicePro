package com.example.workadministration.ui.invoice

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
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

class AddInvoiceBottomSheet : BottomSheetDialogFragment(), AddCustomerBottomSheet.OnCustomerAddedListener {

    interface OnInvoiceSavedListener {
        fun onInvoiceSaved()
    }

    private lateinit var recyclerViewProducts: RecyclerView
    private lateinit var invoiceProductAdapter: InvoiceProductAdapter
    // Ahora almacenamos también purchasePrice en cada producto
    private val selectedProductsList = mutableListOf<Triple<Product, Int, Double>>()
    // (producto, cantidad, purchasePrice)

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnInvoiceSavedListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnInvoiceSavedListener")
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(
            com.google
                .android
                .material
                .R
                .id
                .design_bottom_sheet)
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
        view.findViewById<Button>(R.id.btnAddCustomProduct).setOnClickListener {
            showAddCustomProductDialog()
        }

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

        etPurchasePriceTotal.doOnTextChanged { text, _, _, _ ->
            totalPurchasePrice = text.toString().toDoubleOrNull() ?: 0.0
        }


        recyclerViewProducts.layoutManager = LinearLayoutManager(requireContext())
        invoiceProductAdapter = InvoiceProductAdapter(
            selectedProductsList,
            onQuantityChanged = { _, _ -> updateTotal() },
            onPurchasePriceChanged = { _, _ -> updateTotal() }, // 👈 NUEVO callback
            onEditCustomProduct = { productId -> showEditCustomProductDialog(productId) },
            onDeleteProduct = { position ->
                selectedProductsList.removeAt(position)
                invoiceProductAdapter.notifyItemRemoved(position)
                updateTotal()
            }
        )

        recyclerViewProducts.adapter = invoiceProductAdapter
        (recyclerViewProducts.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false

        // Drag & Drop
        val itemTouchHelperCallback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.clearFocus()
                }
                super.onSelectedChanged(viewHolder, actionState)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                invoiceProductAdapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                recyclerView.adapter?.notifyDataSetChanged()
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = true
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerViewProducts)

        btnSave.setOnClickListener { saveInvoice() }
        btnCancel.setOnClickListener { dismiss() }

        return view
    }

    private fun loadClients() {
        db.collection("customers").get().addOnSuccessListener { documents ->
            allCustomers = documents.map { it.toObject(Customer::class.java).copy(id = it.id) }
            val names = allCustomers.map { it.fullname }

            customerAdapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                names
            )
            autoCompleteClient.setAdapter(customerAdapter)
            autoCompleteClient.threshold = 1 // ⬅️ Mostrar desde 1 letra

            // Mostrar dropdown al enfocar
            autoCompleteClient.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) autoCompleteClient.showDropDown()
            }

            // Mostrar dropdown al hacer click
            autoCompleteClient.setOnClickListener { autoCompleteClient.showDropDown() }

            // Selección de cliente
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
            val productAdapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                productNames
            )
            autoCompleteProduct.setAdapter(productAdapter)
            autoCompleteProduct.threshold = 1 // ⬅️ Mostrar desde 1 letra

            // Mostrar dropdown al enfocar
            autoCompleteProduct.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) autoCompleteProduct.showDropDown()
            }

            // Mostrar dropdown al hacer click
            autoCompleteProduct.setOnClickListener { autoCompleteProduct.showDropDown() }

            // Selección de producto
            autoCompleteProduct.setOnItemClickListener { _, _, position, _ ->
                val name = productAdapter.getItem(position)
                val product = allProducts.find { it.name == name }
                product?.let {
                    selectedProductsList.add(Triple(it, 1, 0.0))
                    invoiceProductAdapter.notifyItemInserted(selectedProductsList.size - 1)
                    updateTotal()
                    autoCompleteProduct.setText("")
                }
            }
        }
    }

    private fun showAddCustomProductDialog() { /* sin cambios */ }

    private fun showEditCustomProductDialog(productId: String) { /* sin cambios */ }

    private fun updateTotal() {
        subtotal = selectedProductsList.sumOf { (product, quantity, _) -> product.price * quantity }
        totalPurchasePrice = selectedProductsList.sumOf { (_, quantity, purchasePrice) -> purchasePrice * quantity }
        val total = subtotal + extraCharges

        tvSubtotalAmount.text = "$%.2f".format(subtotal)
        tvTotalAmount.text = "$%.2f".format(total)
        etPurchasePriceTotal.setText("%.2f".format(totalPurchasePrice))

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
            "totalPurchasePrice" to totalPurchasePrice, // 👈 agregado
            "total" to total
        )

        invoiceRef.set(invoiceData).addOnSuccessListener {
            val detailsCollection = invoiceRef.collection("invoiceDetails")
            selectedProductsList.forEachIndexed { index, (product, quantity, purchasePrice) ->
                val detail = hashMapOf(
                    "productId" to product.id,
                    "name" to product.name,
                    "price" to product.price,
                    "purchasePrice" to purchasePrice, // 👈 agregado
                    "quantity" to quantity,
                    "position" to index
                )
                detailsCollection.add(detail)
            }

            Toast.makeText(requireContext(), "Invoice saved successfully", Toast.LENGTH_SHORT).show()
            listener.onInvoiceSaved()
            dismiss()
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Error saving invoice", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCustomerAdded(customer: Customer) { /* sin cambios */ }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
