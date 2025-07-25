package com.example.workadministration.ui.appointment

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.example.workadministration.ui.NavigationUtil
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class AppointmentActivity : AppCompatActivity(),
    AddAppointmentBottomSheet.OnAppointmentAddedListener,
    EditAppointmentBottomSheet.OnAppointmentUpdatedListener {

    private lateinit var recyclerAppointments: RecyclerView
    private lateinit var searchAppointment: EditText
    private lateinit var adapter: AppointmentAdapter
    private val appointmentsList = mutableListOf<Appointment>()
    private val db = FirebaseFirestore.getInstance()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appointment)

        val addButton = findViewById<ImageButton>(R.id.btnAgregarCita)
        addButton.setOnClickListener {
            val bottomSheet = AddAppointmentBottomSheet()
            bottomSheet.show(supportFragmentManager, "AddAppointmentBottomSheet")
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
        db.collection("appointments")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                appointmentsList.clear()
                for (document in documents) {
                    val customer = document.toObject(Appointment::class.java).copy(id = document.id)
                    appointmentsList.add(customer)
                }
                adapter.actualizarLista(appointmentsList)
            }
    }

    private fun filtrarCitas(texto: String) {
        val filteredList = appointmentsList.filter {
            it.customerName.contains(texto, ignoreCase = true)
        }
        adapter.actualizarLista(filteredList)
    }

    private fun eliminarCita(appointment: Appointment) {
        AlertDialog.Builder(this)
            .setTitle("Confirmation")
            .setMessage("Do you want to delete this appointment with ${appointment.customerName}?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("appointments").document(appointment.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Appointment deleted", Toast.LENGTH_SHORT).show()
                        appointmentsList.remove(appointment)
                        adapter.actualizarLista(appointmentsList)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error deleting appointment", Toast.LENGTH_SHORT).show()
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
}
