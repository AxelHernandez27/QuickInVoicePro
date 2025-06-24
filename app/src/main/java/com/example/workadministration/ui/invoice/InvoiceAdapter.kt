package com.example.workadministration.ui.invoice

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.example.workadministration.R
import androidx.recyclerview.widget.RecyclerView

class InvoiceAdapter(
    private var invoices: List<Invoice>,
    private val context: Context,
    private val onEditClick: (Invoice) -> Unit,
    private val onDeleteClick: (Invoice) -> Unit
) : RecyclerView.Adapter<InvoiceAdapter.InvoiceViewHolder>() {

    class InvoiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val customerName: TextView = itemView.findViewById(R.id.tvNombreCliente)
        val date: TextView = itemView.findViewById(R.id.tvFechaTicket)
        val address: TextView = itemView.findViewById(R.id.tvDireccionCliente)
        val total: TextView = itemView.findViewById(R.id.tvTotalTicket)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditar)
        val btnDelete: View = itemView.findViewById(R.id.btnEliminar)
        val btnGeneratePDF: View = itemView.findViewById(R.id.btnGenerarPDF)
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

        holder.btnDelete.setOnClickListener {
            onDeleteClick(invoice)
        }
        holder.btnEdit.setOnClickListener {
            onEditClick(invoice)
        }
        holder.btnGeneratePDF.setOnClickListener {
            val intent = Intent(context, GeneratePdfActivity::class.java)
            intent.putExtra("invoiceId", invoice.id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = invoices.size

    fun updateList(newList: List<Invoice>) {
        invoices = newList
        notifyDataSetChanged()
    }
}
