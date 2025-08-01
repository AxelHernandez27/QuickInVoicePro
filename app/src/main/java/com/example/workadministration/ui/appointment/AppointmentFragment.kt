package com.example.workadministration.ui.appointment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL

class AppointmentFragment : Fragment(),
    AddAppointmentBottomSheet.OnAppointmentAddedListener,
    EditAppointmentBottomSheet.OnAppointmentUpdatedListener {

    private lateinit var recyclerAppointments: RecyclerView
    private lateinit var searchAppointment: EditText
    private lateinit var adapter: AppointmentAdapter
    private val appointmentsList = mutableListOf<Appointment>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.activity_appointment, container, false)

        val addButton = view.findViewById<ImageButton>(R.id.btnAgregarCita)
        recyclerAppointments = view.findViewById(R.id.citasRecyclerView)
        searchAppointment = view.findViewById(R.id.buscarCita)

        adapter = AppointmentAdapter(appointmentsList, { appointment ->
            eliminarCita(appointment)
        }, { appointment ->
            val editBottomSheet = EditAppointmentBottomSheet.newInstance(appointment.id)
            editBottomSheet.setOnAppointmentUpdatedListener(this)
            editBottomSheet.show(parentFragmentManager, "EditAppointmentBottomSheet")
        })

        recyclerAppointments.layoutManager = LinearLayoutManager(requireContext())
        recyclerAppointments.adapter = adapter

        addButton.setOnClickListener {
            val bottomSheet = AddAppointmentBottomSheet()
            bottomSheet.setOnAppointmentAddedListener(this) // ← AQUÍ
            bottomSheet.show(parentFragmentManager, "AddAppointmentBottomSheet")
        }

        getAppointments()

        searchAppointment.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filtrarCitas(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        return view
    }

    private fun getAppointments() {
        db.collection("appointments")
            .orderBy("date")
            .get()
            .addOnSuccessListener { documents ->
                appointmentsList.clear()
                for (document in documents) {
                    val customer = document.toObject(Appointment::class.java).copy(id = document.id)
                    appointmentsList.add(customer)
                }
                adapter.actualizarLista(appointmentsList.toList())
            }
    }

    private fun filtrarCitas(texto: String) {
        val filteredList = appointmentsList.filter {
            it.customerName.contains(texto, ignoreCase = true)
        }
        adapter.actualizarLista(filteredList)
    }

    private fun eliminarCita(appointment: Appointment) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmation")
            .setMessage("Do you want to delete this appointment with ${appointment.customerName}?")
            .setPositiveButton("Delete") { _, _ ->

                val prefs = requireContext().getSharedPreferences("my_prefs", AppCompatActivity.MODE_PRIVATE)
                val accessToken = prefs.getString("ACCESS_TOKEN", null)

                if (appointment.eventId != null && accessToken != null) {
                    // Eliminar evento de Google Calendar en hilo aparte
                    Thread {
                        var eliminadoCalendario = false
                        try {
                            val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events/${appointment.eventId}")
                            val connection = url.openConnection() as HttpURLConnection
                            connection.requestMethod = "DELETE"
                            connection.setRequestProperty("Authorization", "Bearer $accessToken")
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000

                            val responseCode = connection.responseCode
                            connection.disconnect()

                            eliminadoCalendario = responseCode == 204
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        requireActivity().runOnUiThread {
                            if (eliminadoCalendario) {
                                // Si se eliminó el evento en calendario, eliminar en Firestore
                                db.collection("appointments").document(appointment.id)
                                    .delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(requireContext(), "Appointment deleted", Toast.LENGTH_SHORT).show()
                                        appointmentsList.remove(appointment)
                                        adapter.actualizarLista(appointmentsList)
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(requireContext(), "Error deleting appointment from database", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                Toast.makeText(requireContext(), "Error deleting event from calendar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.start()
                } else {
                    // No hay evento o token, eliminamos directo en Firestore
                    db.collection("appointments").document(appointment.id)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Appointment deleted", Toast.LENGTH_SHORT).show()
                            appointmentsList.remove(appointment)
                            adapter.actualizarLista(appointmentsList)
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(), "Error deleting appointment", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onAppointmentAdded(appointment: Appointment) {
        appointmentsList.add(appointment)
        adapter.actualizarLista(appointmentsList.toList())
    }

    override fun onAppointmentUpdated(updatedAppointment: Appointment) {
        db.collection("appointments").document(updatedAppointment.id)
            .set(updatedAppointment)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Appointment updated successfully", Toast.LENGTH_SHORT).show()
                getAppointments()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error updating appointment", Toast.LENGTH_SHORT).show()
            }
    }
}
