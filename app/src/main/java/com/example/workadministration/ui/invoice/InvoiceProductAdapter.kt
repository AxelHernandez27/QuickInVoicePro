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
import com.example.workadministration.R
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.ui.product.Product
import java.util.Collections
import androidx.core.widget.doOnTextChanged

class InvoiceProductAdapter(
    private val products: MutableList<Pair<Product, Int>>,
    private val onQuantityChanged: () -> Unit,
    private val onEditCustomProduct: (productId: String) -> Unit,
    private val onDeleteProduct: (position: Int) -> Unit
) : RecyclerView.Adapter<InvoiceProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById<TextView>(R.id.tvProductName)
        val tvPrice = view.findViewById<TextView>(R.id.tvProductPrice)
        val etQuantity = view.findViewById<EditText>(R.id.EtProductQuantity)
        val btnIncrease = view.findViewById<Button>(R.id.btnIncreaseQuantity)
        val btnDecrease = view.findViewById<Button>(R.id.btnDecreaseQuantity)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteProduct)
        val btnEdit = view.findViewById<ImageButton>(R.id.btnEditProduct)
        var textWatcher: TextWatcher? = null

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_invoice_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun getItemCount(): Int = products.size

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val (product, quantity) = products[position]

        holder.tvName.text = product.name
        holder.tvPrice.text = "$%.2f".format(product.price)

        // Remueve el watcher viejo si existe para evitar acumulación
        holder.textWatcher?.let {
            holder.etQuantity.removeTextChangedListener(it)
        }

        // Actualiza el texto sin disparar el watcher
        holder.etQuantity.setText(quantity.toString())

        // Crea un nuevo watcher
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val qty = s.toString().toIntOrNull()
                if (qty != null && qty >= 1) {
                    products[position] = product to qty
                    onQuantityChanged()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        // Asigna el nuevo watcher
        holder.etQuantity.addTextChangedListener(watcher)
        holder.textWatcher = watcher

        // El resto de los botones
        holder.btnIncrease.setOnClickListener {
            val newQty = (products.getOrNull(position)?.second ?: quantity) + 1
            products[position] = product to newQty
            notifyItemChanged(position)
            onQuantityChanged()
        }

        holder.btnDecrease.setOnClickListener {
            val currentQty = products.getOrNull(position)?.second ?: quantity
            if (currentQty > 1) {
                val newQty = currentQty - 1
                products[position] = product to newQty
                notifyItemChanged(position)
                onQuantityChanged()
            }
        }

        holder.btnDelete.setOnClickListener {
            onDeleteProduct(position)
        }

        if (product.id.startsWith("custom_")) {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnEdit.setOnClickListener {
                onEditCustomProduct(product.id)
            }
        } else {
            holder.btnEdit.visibility = View.GONE
        }
    }

    // Función para intercambiar posiciones (drag & drop)
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
        onQuantityChanged()
    }
}
