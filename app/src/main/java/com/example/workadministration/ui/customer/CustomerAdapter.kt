package com.example.workadministration.ui.customer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R

class CustomerAdapter(
    private var clientes: List<Customer>,
    private val onDeleteClick: (Customer) -> Unit,
    private val onEditClick: (Customer) -> Unit
) : RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder>() {

    class CustomerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombreCliente: TextView = view.findViewById(R.id.tvNombreCliente)
        val tvEmailCliente: TextView = view.findViewById(R.id.tvEmailCliente)
        val tvDireccionCliente: TextView = view.findViewById(R.id.tvDireccionCliente)
        val tvTelefonoCliente: TextView = view.findViewById(R.id.tvTelefonoCliente)
        val btnEditar: ImageButton = view.findViewById(R.id.btnEditar)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_customer, parent, false)
        return CustomerViewHolder(view)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        val cliente = clientes[position]

        holder.tvNombreCliente.text = cliente.fullname
        holder.tvEmailCliente.text = cliente.email
        holder.tvDireccionCliente.text = cliente.address
        holder.tvTelefonoCliente.text = cliente.phone

        holder.btnEditar.setOnClickListener {
            onEditClick(cliente)
        }

        holder.btnEliminar.setOnClickListener {
            onDeleteClick(cliente)
        }
    }

    override fun getItemCount() = clientes.size

    fun actualizarLista(nuevaLista: List<Customer>) {
        clientes = nuevaLista
        notifyDataSetChanged()
    }
}
