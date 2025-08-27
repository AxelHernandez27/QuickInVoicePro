package com.example.workadministration.ui.invoice

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import com.example.workadministration.R
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.ui.product.Product
import java.util.Collections

class InvoiceProductAdapter(
    private val products: MutableList<Triple<Product, Int, Double>>,
    private val onQuantityChanged: (position: Int, newQty: Int) -> Unit,  // 👈 ahora con params
    private val onPurchasePriceChanged: (Int, Double) -> Unit, // 👈 nuevo callback
    private val onEditCustomProduct: (productId: String) -> Unit,
    private val onDeleteProduct: (position: Int) -> Unit
) : RecyclerView.Adapter<InvoiceProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById<TextView>(R.id.tvProductName)
        val tvPrice = view.findViewById<TextView>(R.id.tvProductPrice)
        val etQuantity = view.findViewById<EditText>(R.id.EtProductQuantity)
        val etPurchasePrice: EditText = itemView.findViewById(R.id.etPurchasePrice) // 👈 agrega este campo en tu layout
        val btnIncrease = view.findViewById<Button>(R.id.btnIncreaseQuantity)
        val btnDecrease = view.findViewById<Button>(R.id.btnDecreaseQuantity)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteProduct)
        val btnEdit = view.findViewById<ImageButton>(R.id.btnEditProduct)
        var textWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invoice_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun getItemCount(): Int = products.size

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val (product, quantity, purchasePrice) = products[position]

        holder.tvName.text = product.name
        holder.tvPrice.text = "$%.2f".format(product.price)

        // 🧹 Evitar múltiples watchers
        holder.textWatcher?.let {
            holder.etQuantity.removeTextChangedListener(it)
        }

        holder.etQuantity.setText(quantity.toString())
        holder.etPurchasePrice.setText(purchasePrice.toString())

        // ✅ Watcher para cantidad
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val qty = s.toString().toIntOrNull()
                if (qty != null && qty >= 1) {
                    // Reemplazamos con un Triple, NO Pair
                    products[holder.adapterPosition] = Triple(product, qty, products[holder.adapterPosition].third)
                    onQuantityChanged(holder.adapterPosition, qty)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        holder.etQuantity.addTextChangedListener(watcher)
        holder.textWatcher = watcher

        // ➕ Aumentar cantidad
        holder.btnIncrease.setOnClickListener {
            val newQty = (products.getOrNull(holder.adapterPosition)?.second ?: quantity) + 1
            products[holder.adapterPosition] = Triple(product, newQty, products[holder.adapterPosition].third)
            notifyItemChanged(holder.adapterPosition)
            onQuantityChanged(holder.adapterPosition, newQty)
        }

        // ➖ Disminuir cantidad
        holder.btnDecrease.setOnClickListener {
            val currentQty = products.getOrNull(holder.adapterPosition)?.second ?: quantity
            if (currentQty > 1) {
                val newQty = currentQty - 1
                products[holder.adapterPosition] = Triple(product, newQty, products[holder.adapterPosition].third)
                notifyItemChanged(holder.adapterPosition)
                onQuantityChanged(holder.adapterPosition, newQty)
            }
        }

        // 💰 actualizar purchasePrice
        holder.etPurchasePrice.doOnTextChanged { text, _, _, _ ->
            val price = text.toString().toDoubleOrNull() ?: 0.0
            products[position] = products[position].copy(third = price)
            onPurchasePriceChanged(position, price)
        }

        // 🗑️ Eliminar
        holder.btnDelete.setOnClickListener {
            onDeleteProduct(holder.adapterPosition)
        }

        // ✏️ Editar solo si es custom
        if (product.id.startsWith("custom_")) {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnEdit.setOnClickListener {
                onEditCustomProduct(product.id)
            }
        } else {
            holder.btnEdit.visibility = View.GONE
        }
    }

    // 🔄 Soporte drag & drop
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(products, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(products, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        //onQuantityChanged()
    }
}
