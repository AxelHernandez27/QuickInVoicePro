package com.example.workadministration.ui.invoice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.workadministration.R
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.ui.product.Product

class InvoiceAdapter(
    private var invoices: List<Invoice>
) : RecyclerView.Adapter<InvoiceAdapter.InvoiceViewHolder>() {

    class InvoiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val customerName: TextView = itemView.findViewById(R.id.tvNombreCliente)
        val date: TextView = itemView.findViewById(R.id.tvFechaTicket)
        val address: TextView = itemView.findViewById(R.id.tvDireccionCliente)
        val total: TextView = itemView.findViewById(R.id.tvTotalTicket)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvoiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ticket, parent, false)
        return InvoiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: InvoiceViewHolder, position: Int) {
        val invoice = invoices[position]
        holder.customerName.text = invoice.customerName
        holder.date.text = invoice.date
        holder.address.text = invoice.customerAddress
        holder.total.text = "$${invoice.total}"
    }

    override fun getItemCount(): Int = invoices.size

    fun updateList(newList: List<Invoice>) {
        invoices = newList
        notifyDataSetChanged()
    }
}
