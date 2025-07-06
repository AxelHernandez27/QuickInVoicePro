package com.example.workadministration.ui.appointment

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.example.workadministration.R
import com.example.workadministration.ui.customer.Customer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AddAppointmentBottomSheet : BottomSheetDialogFragment() {

    interface OnAppointmentAddedListener {
        fun onAppointmentAdded(appointment: Appointment)
    }

    private lateinit var listener: OnAppointmentAddedListener
    private lateinit var autoCompleteClient: AutoCompleteTextView
    private lateinit var etDate: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private val db = FirebaseFirestore.getInstance()
    private var allCustomers = listOf<Customer>()
    private var selectedCustomer: Customer? = null
    private var selectedDate: Date? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnAppointmentAddedListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnAppointmentAddedListener")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.form_cita_agregar, container, false)

        autoCompleteClient = view.findViewById(R.id.autoCompleteClient)
        etDate = view.findViewById(R.id.etDate)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)

        loadClients()

        etDate.setOnClickListener { openDateTimePicker() }

        btnSave.setOnClickListener { saveAppointment() }
        btnCancel.setOnClickListener { dismiss() }

        return view
    }

    private fun loadClients() {
        db.collection("customers").get()
            .addOnSuccessListener { documents ->
                allCustomers = documents.map { it.toObject(Customer::class.java).copy(id = it.id) }
                val names = allCustomers.map { it.fullname }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
                autoCompleteClient.setAdapter(adapter)

                autoCompleteClient.setOnItemClickListener { _, _, position, _ ->
                    val name = adapter.getItem(position)
                    selectedCustomer = allCustomers.find { it.fullname == name }
                    autoCompleteClient.error = null // Limpiar el error al seleccionar un cliente válido
                }

                autoCompleteClient.setOnClickListener {
                    if (autoCompleteClient.adapter != null) autoCompleteClient.showDropDown()
                }
                autoCompleteClient.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && autoCompleteClient.adapter != null) autoCompleteClient.showDropDown()
                }
            }
    }

    private fun openDateTimePicker() {
        val now = Calendar.getInstance()
        val calendar = Calendar.getInstance()

        DatePickerDialog(requireContext(), { _, year, month, day ->
            calendar.set(year, month, day)

            TimePickerDialog(requireContext(), { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                val selected = calendar.time

                // Validación de no permitir fechas/horas pasadas
                if (selected.before(now.time)) {
                    Toast.makeText(requireContext(), "You cannot select a past date or time", Toast.LENGTH_SHORT).show()
                    etDate.text.clear()
                    selectedDate = null
                } else {
                    selectedDate = selected
                    val formatter = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale("en", "US"))
                    etDate.setText(formatter.format(selectedDate!!))
                }
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()

        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = now.timeInMillis // Bloquear fechas anteriores al día actual
        }.show()
    }

    private fun saveAppointment() {
        val clientName = autoCompleteClient.text.toString().trim()

        // Validación campo cliente
        if (clientName.isEmpty() || selectedCustomer == null) {
            autoCompleteClient.error = "Please select a client"
            return
        }

        // Validación campo fecha
        if (selectedDate == null) {
            etDate.error = "Please select date and time"
            return
        }

        val appointmentData = hashMapOf(
            "customerId" to selectedCustomer!!.id,
            "customerName" to selectedCustomer!!.fullname,
            "customerPhone" to selectedCustomer!!.phone,
            "date" to Timestamp(selectedDate!!),
            "status" to "pendiente"
        )

        db.collection("appointments").add(appointmentData)
            .addOnSuccessListener { docRef ->
                val appointment = Appointment(
                    id = docRef.id,
                    customerId = selectedCustomer!!.id,
                    customerName = selectedCustomer!!.fullname,
                    customerPhone = selectedCustomer!!.phone,
                    date = selectedDate!!,
                    status = "pendiente"
                )
                listener.onAppointmentAdded(appointment)
                Toast.makeText(requireContext(), "Appointment added successfully", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error saving appointment", Toast.LENGTH_SHORT).show()
            }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
