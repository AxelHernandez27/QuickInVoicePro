package com.example.workadministration.ui.appointment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.ContextCompat

class AppointmentAdapter(
    private var appointments: List<Appointment>,
    private val onDeleteClick: (Appointment) -> Unit,
    private val onEditClick: (Appointment) -> Unit
) : RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {

    class AppointmentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombreClienteCita: TextView = view.findViewById(R.id.tvNombreClienteCita)
        val tvFechaCita: TextView = view.findViewById(R.id.tvFechaCita)
        val tvTelefonoCliente: TextView = view.findViewById(R.id.tvTelefonoCliente)
        val btnEditar: ImageButton = view.findViewById(R.id.btnEditar)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cita, parent, false)
        return AppointmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        val cita = appointments[position]

        holder.tvNombreClienteCita.text = cita.customerName
        holder.tvTelefonoCliente.text = cita.customerPhone

        // Formato MM/dd/yyyy hh:mm a (12hrs con AM/PM)
        val formatter = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale("en", "US"))
        holder.tvFechaCita.text = "${formatter.format(cita.date)}"

        // Función para limpiar hora, minutos, segundos y milisegundos de un Date
        fun clearTime(date: Date): Date {
            val cal = Calendar.getInstance()
            cal.time = date
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.time
        }

        val context = holder.itemView.context
        val cardView = holder.itemView.findViewById<CardView>(R.id.cardCita)

        val hoySinHora = clearTime(Date())            // Hoy sin hora
        val citaSinHora = clearTime(cita.date)        // Fecha de la cita sin hora

        val diffInMillis = citaSinHora.time - hoySinHora.time
        val diffInDaysDouble = diffInMillis.toDouble() / (1000 * 60 * 60 * 24)
        val diffInDays = Math.ceil(diffInDaysDouble).toInt()

        when {
            diffInDays in 0..3 -> {
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.red))
            }
            diffInDays in 4..7 -> {
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.yellow))
            }
            diffInDays >= 8 -> {
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.blue))
            }
        }
        holder.btnEditar.setOnClickListener {
            onEditClick(cita)
        }

        holder.btnEliminar.setOnClickListener {
            onDeleteClick(cita)
        }
    }

    override fun getItemCount() = appointments.size

    fun actualizarLista(nuevaLista: List<Appointment>) {
        appointments = nuevaLista
        notifyDataSetChanged()
    }
}
