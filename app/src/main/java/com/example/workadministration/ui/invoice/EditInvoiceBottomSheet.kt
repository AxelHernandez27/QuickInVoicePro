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
import java.util.Calendar
import java.util.UUID
import java.util.Collections
import java.util.Date

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
    private lateinit var etPurchasePriceTotal: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var btnAddCustomProduct: Button

    private lateinit var purchasePriceWatcher: TextWatcher

    private lateinit var rvProducts: RecyclerView
    private lateinit var invoiceAdapter: InvoiceProductAdapter
    private val selectedProductsWithQty = mutableListOf<Triple<Product, Int, Double>>() // Triple(product, quantity, purchasePrice)

    private val db = FirebaseFirestore.getInstance()
    private var allProducts = listOf<Product>()
    private var allCustomers = listOf<Customer>()

    private var selectedCustomer: Customer? = null
    private var subtotal = 0.0
    private var totalPurchasePrice  = 0.0
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
        etPurchasePriceTotal = view.findViewById(R.id.etPurchasePriceTotal)
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

        purchasePriceWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val newTotal = s.toString().toDoubleOrNull()
                if (newTotal != null && newTotal != totalPurchasePrice) {
                    totalPurchasePrice = newTotal
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        etPurchasePriceTotal.addTextChangedListener(purchasePriceWatcher)


        rvProducts = view.findViewById(R.id.rvProducts)

        invoiceAdapter = InvoiceProductAdapter(
            selectedProductsWithQty,
            onQuantityChanged = { position, newQty ->
                val (product, _, purchasePrice) = selectedProductsWithQty[position]
                selectedProductsWithQty[position] = Triple(product, newQty, purchasePrice)
                invoiceAdapter.notifyItemChanged(position)
                updateTotal()
            },
            onPurchasePriceChanged = { position, newPrice ->
                val (product, qty, _) = selectedProductsWithQty[position]
                selectedProductsWithQty[position] = Triple(product, qty, newPrice)
                updateTotal()
            },
            onEditCustomProduct = { productId ->
                val index = selectedProductsWithQty.indexOfFirst { it.first.id == productId }
                if (index != -1) {
                    val (product, qty, purchasePrice) = selectedProductsWithQty[index]
                    showEditCustomProductDialog(product) { updatedProduct ->
                        selectedProductsWithQty[index] = Triple(updatedProduct, qty, purchasePrice)
                        invoiceAdapter.notifyItemChanged(index)
                        updateTotal()
                    }
                }
            }
        ) { position ->
            selectedProductsWithQty.removeAt(position)
            invoiceAdapter.notifyItemRemoved(position)
            updateTotal()
        }

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
                            val purchasePrice = detailDoc.getDouble("purchasePrice") ?: 0.0
                            selectedProductsWithQty.add(Triple(product, quantity, purchasePrice))
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
                product?.let { p ->
                    val index = selectedProductsWithQty.indexOfFirst { it.first.id == p.id }
                    if (index != -1) {
                        // Producto ya existe → aumentar cantidad en 1
                        val (prod, qty, purchasePrice) = selectedProductsWithQty[index]
                        selectedProductsWithQty[index] = Triple(prod, qty + 1, purchasePrice)
                        invoiceAdapter.notifyItemChanged(index)
                    } else {
                        // Producto nuevo
                        selectedProductsWithQty.add(Triple(p, 1, p.price))
                        invoiceAdapter.notifyItemInserted(selectedProductsWithQty.size - 1)
                    }
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
                    selectedProductsWithQty.add(Triple(product, 1, price))
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
                    onSaved(updated)
                } else {
                    Toast.makeText(requireContext(), "Invalid data", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun updateTotal() {
        subtotal = selectedProductsWithQty.sumOf { (product, qty) -> product.price * qty }
        totalPurchasePrice = selectedProductsWithQty.sumOf { (_, qty, purchasePrice) -> purchasePrice * qty }
        val total = subtotal + extraCharges

        tvSubtotalAmount.text = "$%.2f".format(subtotal)
        tvTotalAmount.text = "$%.2f".format(total)

        // Actualiza el campo sin disparar recursivamente el TextWatcher
        etPurchasePriceTotal.removeTextChangedListener(purchasePriceWatcher)
        etPurchasePriceTotal.setText("%.2f".format(totalPurchasePrice))
        etPurchasePriceTotal.addTextChangedListener(purchasePriceWatcher)
    }

    private fun updateInvoice() {
        if (selectedCustomer == null) {
            Toast.makeText(requireContext(), "Please select a client", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedProductsWithQty.isEmpty()) {
            Toast.makeText(requireContext(), "Please add at least one product", Toast.LENGTH_SHORT).show()
            return
        }

        val notes = etAdditionalNotes.text.toString().trim()
        val total = subtotal + extraCharges

        invoiceId?.let { id ->
            val invoiceRef = db.collection("invoices").document(id)
            // Primero obtenemos los valores antiguos
            invoiceRef.get().addOnSuccessListener { oldDoc ->
                val oldTotal = oldDoc.getDouble("total") ?: 0.0
                val oldMaterials = oldDoc.getDouble("totalPurchasePrice") ?: 0.0

                // Ahora guardamos la factura con los nuevos valores
                val invoiceData = mapOf(
                    "customerId" to selectedCustomer!!.id,
                    "customerName" to selectedCustomer!!.fullname,
                    "customerAddress" to selectedCustomer!!.address,
                    "extraCharges" to extraCharges,
                    "totalPurchasePrice" to totalPurchasePrice,
                    "notes" to notes,
                    "total" to total
                )

                invoiceRef.set(invoiceData, SetOptions.merge()).addOnSuccessListener {
                    val detailsRef = invoiceRef.collection("invoiceDetails")
                    detailsRef.get().addOnSuccessListener { existing ->
                        existing.forEach { it.reference.delete() }
                        selectedProductsWithQty.forEachIndexed { index, (product, qty, purchasePrice) ->
                            val detail = mapOf(
                                "productId" to product.id,
                                "name" to product.name,
                                "price" to product.price,
                                "purchasePrice" to purchasePrice,
                                "quantity" to qty,
                                "position" to index
                            )
                            detailsRef.add(detail)
                        }
                        // Actualizamos reporte con valores antiguos y nuevos
                        updateMonthlyReportOnEdit(id, oldTotal, oldMaterials, total, totalPurchasePrice)

                        Toast.makeText(requireContext(), "Invoice updated", Toast.LENGTH_SHORT).show()
                        listener?.onInvoiceUpdated()
                        dismiss()
                    }
                }.addOnFailureListener {
                    Toast.makeText(requireContext(), "Error updating invoice", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateMonthlyReportOnEdit(
        invoiceId: String,
        oldTotal: Double,
        oldMaterials: Double,
        newInvoiceTotal: Double,
        newMaterialsTotal: Double
    ) {
        val invoiceRef = db.collection("invoices").document(invoiceId)
        val date = Date() // o la fecha que tengas guardada
        val calendar = Calendar.getInstance().apply { time = date }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val reportId = "${year}_$month"
        val reportRef = db.collection("reports").document(reportId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(reportRef)
            val currentTotalTickets = snapshot.getDouble("totalTickets") ?: 0.0
            val currentTotalMaterials = snapshot.getDouble("totalMaterials") ?: 0.0
            val currentProfit = snapshot.getDouble("profit") ?: 0.0

            val newTotalTickets = currentTotalTickets - oldTotal + newInvoiceTotal
            val newTotalMaterials = currentTotalMaterials - oldMaterials + newMaterialsTotal
            val newProfit = currentProfit - (oldTotal - oldMaterials) + (newInvoiceTotal - newMaterialsTotal)

            transaction.set(reportRef, mapOf(
                "year" to year,
                "month" to month,
                "totalTickets" to newTotalTickets,
                "totalMaterials" to newTotalMaterials,
                "profit" to newProfit
            ))
        }
    }


}
