package com.example.workadministration.ui.appointment

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.example.workadministration.TokenHelper
import com.example.workadministration.ui.NavigationUtil
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AppointmentActivity : AppCompatActivity(),
    AddAppointmentBottomSheet.OnAppointmentAddedListener,
    EditAppointmentBottomSheet.OnAppointmentUpdatedListener {

    private lateinit var recyclerAppointments: RecyclerView
    private lateinit var searchAppointment: EditText
    private lateinit var adapter: AppointmentAdapter
    private val appointmentsList = mutableListOf<Appointment>()
    private val db = FirebaseFirestore.getInstance()

    private var accessToken: String? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appointment)

        // Intent (puede ser null si no viene)
        accessToken = intent.getStringExtra("ACCESS_TOKEN")

        // Si no viene por Intent, lo lee de SharedPreferences
        if (accessToken == null) {
            accessToken = getSharedPreferences("my_prefs", MODE_PRIVATE).getString("ACCESS_TOKEN", null)
        }

        if (accessToken == null) {
            Log.e("AppointmentActivity", "Token is null, user may not be logged in")
        } else {
            Log.d("AppointmentActivity", "Token obtenido: $accessToken")
        }

        val addButton = findViewById<ImageButton>(R.id.btnAgregarCita)
        addButton.setOnClickListener {
            val bottomSheet = AddAppointmentBottomSheet()
            bottomSheet.setOnAppointmentAddedListener(this@AppointmentActivity)
            bottomSheet.show(supportFragmentManager, "AddAppointmentBottomSheet")
            if (accessToken == null) {
                Toast.makeText(this, "Warning: No access token received", Toast.LENGTH_SHORT).show()
                // Aquí puedes decidir qué hacer si no hay token: bloquear función, etc.
            }
        }

        recyclerAppointments = findViewById(R.id.citasRecyclerView)
        searchAppointment = findViewById(R.id.buscarCita)

        adapter = AppointmentAdapter(appointmentsList, { appointment ->
            eliminarCita(appointment)
        }, { appointment ->
            val editBottomSheet = EditAppointmentBottomSheet.newInstance(appointment.id)
            editBottomSheet.setOnAppointmentUpdatedListener(this)
            editBottomSheet.show(supportFragmentManager, "EditAppointmentBottomSheet")
        })

        recyclerAppointments.layoutManager = LinearLayoutManager(this)
        recyclerAppointments.adapter = adapter

        getAppointments()

        searchAppointment.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filtrarCitas(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        NavigationUtil.setupNavigation(this, bottomNav, R.id.nav_home)
    }

    private fun getAppointments() {
        lifecycleScope.launch {
            try {
                val documents = db.collection("appointments")
                    .orderBy("date", com.google.firebase.firestore.Query.Direction.ASCENDING)
                    .get().await()
                Log.d("All", "Appointments")


                appointmentsList.clear()
                val now = System.currentTimeMillis()

                for (document in documents) {
                    val appointment = document.toObject(Appointment::class.java).copy(id = document.id)
                    val appointmentTime = appointment.date.time

                    val now = System.currentTimeMillis()
                    val tenMinutesAgo = now - (10 * 60 * 1000)

                    Log.d("AutoDeleteCheck", "Appointment ${appointment.id} time: $appointmentTime, now: $now")

                    if (appointmentTime < tenMinutesAgo) {
                        Log.d("AutoDeleteCheck", "Deleting appointment ${appointment.id} automatically")
                        val deleted = eliminarCitaAutomatica(appointment)
                        Log.d("AutoDeleteCheck", "Deleted result: $deleted")
                        if (deleted) {
                            Log.d("AutoDeleteCheck", "Appointment deleted automatically: ${appointment.id}")
                        } else {
                            Log.e("AutoDeleteCheck", "Failed to delete appointment: ${appointment.id}")
                        }
                    } else {
                        appointmentsList.add(appointment)
                    }
                }

                adapter.actualizarLista(appointmentsList)
            } catch (e: Exception) {
                Log.e("AppointmentActivity", "Error getting appointments", e)
            }
        }
    }

    private fun filtrarCitas(texto: String) {
        val filteredList = appointmentsList.filter {
            it.customerName.contains(texto, ignoreCase = true)
        }
        adapter.actualizarLista(filteredList)
    }

    private fun eliminarCita(appointment: Appointment) {
        Log.d("Delete", "Hola, es hora de pelear, $accessToken")

        AlertDialog.Builder(this)
            .setTitle("Confirmation")
            .setMessage("Do you want to delete this appointment with ${appointment.customerName}?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    // 🔄 Refrescar token antes de eliminar evento
                    accessToken = TokenHelper.refreshGoogleToken(this@AppointmentActivity)
                    Log.d("TokenRefresh", "Token después de refrescar: $accessToken")

                    if (accessToken == null) {
                        Toast.makeText(
                            this@AppointmentActivity,
                            "No valid Google token available",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    // ✅ Si hay evento en Calendar, intenta eliminarlo
                    val eventId = appointment.eventId
                    Log.d("AppointmentActivity", "Event ID: $eventId")
                    val calendarDeleted = if (!eventId.isNullOrEmpty()) {
                        eliminarEventoEnGoogleCalendar(eventId)
                    } else {
                        true // No había evento que eliminar
                    }

                    if (!calendarDeleted) {
                        Toast.makeText(
                            this@AppointmentActivity,
                            "Failed to delete calendar event",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    // 🔥 Eliminar cita de Firestore
                    db.collection("appointments").document(appointment.id)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(
                                this@AppointmentActivity,
                                "Appointment deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                            appointmentsList.remove(appointment)
                            adapter.actualizarLista(appointmentsList)
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                this@AppointmentActivity,
                                "Error deleting appointment",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    override fun onAppointmentAdded(appointment: Appointment) {
            appointmentsList.add(appointment)
            adapter.actualizarLista(appointmentsList)
        }

        override fun onAppointmentUpdated(updatedAppointment: Appointment) {
            db.collection("appointments").document(updatedAppointment.id)
                .set(updatedAppointment)
                .addOnSuccessListener {
                    Toast.makeText(this, "Appointment updated successfully", Toast.LENGTH_SHORT).show()
                    getAppointments()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error updating appointment", Toast.LENGTH_SHORT).show()
                }
        }

        private suspend fun eliminarEventoEnGoogleCalendar(eventId: String): Boolean {
            Log.d("Delete Calendar", "Event ID: $eventId")

            return withContext(Dispatchers.IO) {
                try {
                    val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events/$eventId"
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    Log.d("AppointmentActivity", "Event ID: $eventId")
                    Log.d("AppointmentActivity", "Token recibido: $accessToken")

                    conn.requestMethod = "DELETE"
                    conn.setRequestProperty("Authorization", "Bearer $accessToken")
                    conn.connect()
                    Log.d("AppointmentActivity", "conectado")

                    conn.responseCode == 204 // 204 No Content = eliminado correctamente
                } catch (e: Exception) {
                Log.e("CalendarDelete", "Error deleting calendar event", e)
                false
            }
        }
    }

    private suspend fun eliminarCitaAutomatica(appointment: Appointment): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Eliminar de Google Calendar si tiene eventId
                val calendarDeleted = if (!appointment.eventId.isNullOrEmpty() && accessToken != null) {
                    eliminarEventoEnGoogleCalendar(appointment.eventId)
                } else {
                    true
                }

                if (!calendarDeleted) return@withContext false

                // 2. Eliminar de Firestore
                db.collection("appointments").document(appointment.id).delete().await()
                true
            } catch (e: Exception) {
                Log.e("AutoDelete", "Error al eliminar cita pasada", e)
                false
            }
        }
    }


}
