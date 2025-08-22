package com.example.workadministration.ui.invoice

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
import java.util.Collections

class EditInvoiceBottomSheet : BottomSheetDialogFragment(),
    AddCustomerBottomSheet.OnCustomerAddedListener {

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
    private lateinit var tvSubtotalAmount: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var etAdditionalNotes: EditText
    private lateinit var etExtraCharges: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var btnAddCustomProduct: Button

    private lateinit var rvProducts: RecyclerView
    private lateinit var invoiceAdapter: InvoiceProductAdapter
    private val selectedProductsWithQty = mutableListOf<Pair<Product, Int>>() // productos con cantidad

    private val db = FirebaseFirestore.getInstance()
    private var allProducts = listOf<Product>()
    private var allCustomers = listOf<Customer>()

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

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        invoiceId = arguments?.getString(ARG_INVOICE_ID)
        if (invoiceId == null) {
            dismiss()
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.form_ticket_editar, container, false)

        autoCompleteClient = view.findViewById(R.id.autoCompleteClient)
        val btnAddClient = view.findViewById<Button>(R.id.btnAddClient)
        autoCompleteProduct = view.findViewById(R.id.autoCompleteProduct)
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

        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        btnClose.setOnClickListener { dismiss() }

        etExtraCharges.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                extraCharges = s.toString().toDoubleOrNull() ?: 0.0
                updateTotal()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        rvProducts = view.findViewById(R.id.rvProducts)

        invoiceAdapter = InvoiceProductAdapter(
            selectedProductsWithQty,
            onQuantityChanged = { position, newQty ->
                val (prod, _) = selectedProductsWithQty[position]
                selectedProductsWithQty[position] = prod to newQty
                invoiceAdapter.notifyItemChanged(position)
                updateTotal()
            },
            onEditCustomProduct = { productId ->
                val index = selectedProductsWithQty.indexOfFirst { it.first.id == productId }
                if (index != -1) {
                    val product = selectedProductsWithQty[index].first
                    showEditCustomProductDialog(product) { updatedProduct ->
                        selectedProductsWithQty[index] = updatedProduct to selectedProductsWithQty[index].second
                        invoiceAdapter.notifyItemChanged(index)
                        updateTotal()
                    }
                }
            },
            onDeleteProduct = { position ->
                selectedProductsWithQty.removeAt(position)
                invoiceAdapter.notifyItemRemoved(position)
                updateTotal()
            }
        )


        rvProducts.adapter = invoiceAdapter
        rvProducts.layoutManager = LinearLayoutManager(requireContext())

        // ✅ Habilitar drag & drop en edición
        val itemTouchHelper = ItemTouchHelper(object :
            ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                    Collections.swap(selectedProductsWithQty, fromPos, toPos)
                    invoiceAdapter.notifyItemMoved(fromPos, toPos)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(rvProducts)

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
                    .orderBy("position").get().addOnSuccessListener { details ->
                        selectedProductsWithQty.clear()
                        details.forEach { detailDoc ->
                            val product = Product(
                                id = detailDoc.getString("productId") ?: "",
                                name = detailDoc.getString("name") ?: "",
                                price = detailDoc.getDouble("price") ?: 0.0
                            )
                            val quantity = detailDoc.getLong("quantity")?.toInt() ?: 1
                            selectedProductsWithQty.add(product to quantity)
                        }
                        invoiceAdapter.notifyDataSetChanged()
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
            customerAdapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                names
            )
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
        customerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            allCustomers.map { it.fullname }
        )
        autoCompleteClient.setAdapter(customerAdapter)

        selectedCustomer = customer
        autoCompleteClient.setText(customer.fullname, false)
        autoCompleteClient.error = null
        autoCompleteClient.clearFocus()

        Toast.makeText(
            requireContext(),
            "Selected customer: ${customer.fullname}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun loadProducts(onComplete: () -> Unit) {
        db.collection("products").get().addOnSuccessListener { documents ->
            allProducts = documents.map { doc ->
                doc.toObject(Product::class.java).copy(id = doc.id)
            }
            val productNames = allProducts.map { it.name }
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                productNames
            )
            autoCompleteProduct.setAdapter(adapter)

            autoCompleteProduct.setOnItemClickListener { _, _, position, _ ->
                val name = adapter.getItem(position)
                val product = allProducts.find { it.name == name }
                product?.let {
                    selectedProductsWithQty.add(it to 1)
                    invoiceAdapter.notifyItemInserted(selectedProductsWithQty.size - 1)
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
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_custom_product, null)
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
                    selectedProductsWithQty.add(product to 1)
                    invoiceAdapter.notifyItemInserted(selectedProductsWithQty.size - 1)
                    updateTotal()
                } else {
                    Toast.makeText(requireContext(), "Invalid name or price", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCustomProductDialog(product: Product, onSaved: (Product) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_custom_product, null)
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
                    val updated = product.copy(name = name, price = price)
                    onSaved(updated) // ✅ devolvemos un nuevo objeto
                } else {
                    Toast.makeText(requireContext(), "Invalid data", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun updateTotal() {
        subtotal = selectedProductsWithQty.sumOf { (product, qty) -> product.price * qty }
        val total = subtotal + extraCharges
        tvSubtotalAmount.text = "$%.2f".format(subtotal)
        tvTotalAmount.text = "$%.2f".format(total)
    }

    private fun updateInvoice() {
        if (selectedCustomer == null) {
            Toast.makeText(requireContext(), "Please select a client", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedProductsWithQty.isEmpty()) {
            Toast.makeText(requireContext(), "Please add at least one product", Toast.LENGTH_SHORT)
                .show()
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
                    val detailsRef =
                        db.collection("invoices").document(id).collection("invoiceDetails")
                    detailsRef.get().addOnSuccessListener { existing ->
                        existing.forEach { it.reference.delete() }
                        selectedProductsWithQty.forEachIndexed { index, (product, qty) ->
                            val detail = mapOf(
                                "productId" to product.id,
                                "name" to product.name,
                                "price" to product.price,
                                "quantity" to qty,
                                "position" to index // ✅ guarda el orden
                            )
                            detailsRef.add(detail)
                        }
                        Toast.makeText(
                            requireContext(),
                            "Invoice updated",
                            Toast.LENGTH_SHORT
                        ).show()
                        listener?.onInvoiceUpdated()
                        dismiss()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error updating invoice", Toast.LENGTH_SHORT)
                        .show()
                }
        }
    }
}
