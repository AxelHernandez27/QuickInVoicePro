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
    private val selectedProductsList = mutableListOf<Pair<Product, Int>>() // lista ordenada de productos con cantidad

    private lateinit var listener: OnInvoiceSavedListener
    private lateinit var customerAdapter: ArrayAdapter<String>

    private lateinit var autoCompleteClient: AutoCompleteTextView
    private lateinit var autoCompleteProduct: AutoCompleteTextView
    private lateinit var tvSubtotalAmount: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var etAdditionalNotes: EditText
    private lateinit var etExtraCharges: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private val db = FirebaseFirestore.getInstance()
    private var allProducts = listOf<Product>()
    private var allCustomers = listOf<Customer>()

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

        etExtraCharges.doOnTextChanged { text, _, _, _ ->
            extraCharges = text.toString().toDoubleOrNull() ?: 0.0
            updateTotal()
        }

        recyclerViewProducts.layoutManager = LinearLayoutManager(requireContext())
        invoiceProductAdapter = InvoiceProductAdapter(
            selectedProductsList,
            onQuantityChanged = { position: Int, newQty: Int ->
                // opcionalmente puedes actualizar productos aquí si quieres
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
        (recyclerViewProducts.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false


        // Aquí agregamos ItemTouchHelper para drag & drop
        val itemTouchHelperCallback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                val swipeFlags = 0
                return makeMovementFlags(dragFlags, swipeFlags)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.clearFocus() // 👈 Quita foco de EditText
                }
                super.onSelectedChanged(viewHolder, actionState)
            }
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                invoiceProductAdapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                recyclerView.adapter?.notifyDataSetChanged()
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No implementamos swipe
            }

            override fun isLongPressDragEnabled(): Boolean = true
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerViewProducts)

        btnSave.setOnClickListener { saveInvoice() }
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
                    val index = selectedProductsList.indexOfFirst { it.first.id == product.id }
                    if (index != -1) {
                        val (p, qty) = selectedProductsList[index]
                        selectedProductsList[index] = p to qty + 1
                        invoiceProductAdapter.notifyItemChanged(index)
                    } else {
                        selectedProductsList.add(it to 1)
                        invoiceProductAdapter.notifyItemInserted(selectedProductsList.size - 1)
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
                        selectedProductsList.add(customProduct to 1)
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

        val (product, quantity) = selectedProductsList[index]

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
                    selectedProductsList[index] = updatedProduct to quantity
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
        subtotal = selectedProductsList.sumOf { (product, quantity) -> product.price * quantity }
        val total = subtotal + extraCharges
        tvSubtotalAmount.text = "$%.2f".format(subtotal)
        tvTotalAmount.text = "$%.2f".format(total)
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
            "total" to total
        )

        invoiceRef.set(invoiceData).addOnSuccessListener {
            val detailsCollection = invoiceRef.collection("invoiceDetails")
            selectedProductsList.forEachIndexed { index, (product, quantity) ->
                val detail = hashMapOf(
                    "productId" to product.id,
                    "name" to product.name,
                    "price" to product.price,
                    "quantity" to quantity,
                    "position" to index   // <-- nuevo campo posición
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

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
