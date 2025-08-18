package com.example.workadministration.ui.quote

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.example.workadministration.R
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.ui.product.Product

class QuoteProductAdapter(
    private val products: MutableList<Pair<Product, Int>>,
    private val onQuantityChanged: (position: Int, newQty: Int) -> Unit,
    private val onEditCustomProduct: (productId: String) -> Unit,
    private val onDeleteProduct: (position: Int) -> Unit
) : RecyclerView.Adapter<QuoteProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvProductName)
        val tvPrice: TextView = view.findViewById(R.id.tvProductPrice)
        val etQuantity: EditText = view.findViewById(R.id.EtProductQuantity)
        val btnIncrease: Button = view.findViewById(R.id.btnIncreaseQuantity)
        val btnDecrease: Button = view.findViewById(R.id.btnDecreaseQuantity)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteProduct)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditProduct)
        var textWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ProductViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_invoice_product, parent, false))

    override fun getItemCount(): Int = products.size

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val (product, quantity) = products[position]

        holder.tvName.text = product.name
        holder.tvPrice.text = "$%.2f".format(product.price)

        holder.textWatcher?.let { holder.etQuantity.removeTextChangedListener(it) }
        holder.etQuantity.setText(quantity.toString())

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val qty = s.toString().toIntOrNull() ?: 1
                products[holder.adapterPosition] = product.copy() to qty
                onQuantityChanged(holder.adapterPosition, qty)
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        holder.etQuantity.addTextChangedListener(watcher)
        holder.textWatcher = watcher

        holder.btnIncrease.setOnClickListener {
            val newQty = quantity + 1
            products[holder.adapterPosition] = product.copy() to newQty
            notifyItemChanged(holder.adapterPosition)
            onQuantityChanged(holder.adapterPosition, newQty)
        }

        holder.btnDecrease.setOnClickListener {
            if (quantity > 1) {
                val newQty = quantity - 1
                products[holder.adapterPosition] = product.copy() to newQty
                notifyItemChanged(holder.adapterPosition)
                onQuantityChanged(holder.adapterPosition, newQty)
            }
        }

        holder.btnDelete.setOnClickListener { onDeleteProduct(holder.adapterPosition) }

        if (product.id.startsWith("custom_")) {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnEdit.setOnClickListener { onEditCustomProduct(product.id) }
        } else holder.btnEdit.visibility = View.GONE
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) products.swap(i, i + 1)
        } else {
            for (i in fromPosition downTo toPosition + 1) products.swap(i, i - 1)
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    private fun <T> MutableList<T>.swap(i: Int, j: Int) { val tmp = this[i]; this[i] = this[j]; this[j] = tmp }
}
