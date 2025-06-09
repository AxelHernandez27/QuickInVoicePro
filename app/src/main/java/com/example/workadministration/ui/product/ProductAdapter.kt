package com.example.workadministration.ui.product

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R

class ProductAdapter (
    private var products: List<Product>,
    private val onDeleteClick: (Product) -> Unit,
    private val onEditClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombreProducto: TextView = view.findViewById(R.id.tvNombreProducto)
        val tvPrecioProducto: TextView = view.findViewById(R.id.tvPrecioProducto)
        val tvDescripcionProducto: TextView = view.findViewById(R.id.tvDescripcionProducto)
        // val tvCategoriaProducto: TextView = view.findViewById(R.id.tvCategoriaProducto)
        val btnEditar: ImageButton = view.findViewById(R.id.btnEditar)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    @SuppressLint("DefaultLocale")
    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]

        holder.tvNombreProducto.text = product.name
        holder.tvPrecioProducto.text = String.format("$%,.2f", product.price)
        holder.tvDescripcionProducto.text = product.description
        // holder.tvCategoriaProducto.text = product.category

        holder.btnEditar.setOnClickListener {
            onEditClick(product)
        }

        holder.btnEliminar.setOnClickListener {
            onDeleteClick(product)
        }
    }

    override fun getItemCount() = products.size

    fun updateList(newList: List<Product>) {
        products = newList
        notifyDataSetChanged()
    }
}