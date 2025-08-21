package com.example.workadministration.ui.quote

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.example.workadministration.R
import androidx.recyclerview.widget.RecyclerView

class QuoteAdapter (
    private var quotes: List<Quote>,
    private val context: Context,
    private val onEditClick: (Quote) -> Unit,
    private val onDeleteClick: (Quote) -> Unit,
    private val onItemClick: (Quote) -> Unit // 👈 agregamos este callback

) : RecyclerView.Adapter<QuoteAdapter.QuoteViewHolder>() {

    class QuoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val customerName: TextView = itemView.findViewById(R.id.tvNombreCliente)
        val date: TextView = itemView.findViewById(R.id.tvFechaTicket)
        val address: TextView = itemView.findViewById(R.id.tvDireccionCliente)
        val total: TextView = itemView.findViewById(R.id.tvTotalTicket)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditar)
        val btnDelete: View = itemView.findViewById(R.id.btnEliminar)
        val btnGeneratePDF: View = itemView.findViewById(R.id.btnGenerarPDF)

        private lateinit var quoteAdapter: QuoteAdapter
        private val quotesList = mutableListOf<Quote>()

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ticket, parent, false)
        return QuoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuoteViewHolder, position: Int) {
        val quote = quotes[position]
        holder.customerName.text = quote.customerName
        holder.date.text = quote.date
        holder.address.text = quote.customerAddress
        holder.total.text = "$${quote.total}"

        holder.btnDelete.setOnClickListener {
            onDeleteClick(quote)
        }
        holder.btnEdit.setOnClickListener {
            onEditClick(quote)
        }

        // 👇 Click en todo el item para convertir
        holder.itemView.setOnClickListener { onItemClick(quote) }

        holder.btnGeneratePDF.setOnClickListener {
            val intent = Intent(context, GeneratePdfActivity::class.java)
            intent.putExtra("quoteId", quote.id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = quotes.size

    fun updateList(newList: List<Quote>) {
        quotes = newList
        notifyDataSetChanged()
    }
}
