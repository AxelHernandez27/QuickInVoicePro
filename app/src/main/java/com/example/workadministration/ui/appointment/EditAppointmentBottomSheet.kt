package com.example.workadministration.ui.appointment

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.workadministration.R
import com.example.workadministration.ui.customer.Customer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class EditAppointmentBottomSheet : BottomSheetDialogFragment() {

    interface OnAppointmentUpdatedListener {
        fun onAppointmentUpdated(updatedAppointment: Appointment)
    }

    private var listener: OnAppointmentUpdatedListener? = null

    fun setOnAppointmentUpdatedListener(listener: OnAppointmentUpdatedListener) {
        this.listener = listener
    }

    private lateinit var autoCompleteClient: AutoCompleteTextView
    private lateinit var etDateTime: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private val db = FirebaseFirestore.getInstance()
    private var allCustomers = listOf<Customer>()
    private var selectedCustomer: Customer? = null
    private var appointmentDate: Date? = null

    private var appointmentId: String? = null
    private var currentPhone: String? = null

    companion object {
        private const val ARG_APPOINTMENT_ID = "appointmentId"

        fun newInstance(appointmentId: String): EditAppointmentBottomSheet {
            val fragment = EditAppointmentBottomSheet()
            val bundle = Bundle()
            bundle.putString(ARG_APPOINTMENT_ID, appointmentId)
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appointmentId = arguments?.getString(ARG_APPOINTMENT_ID)
        if (appointmentId == null) {
            dismiss()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.form_cita_editar, container, false)

        autoCompleteClient = view.findViewById(R.id.autoCompleteClient)
        etDateTime = view.findViewById(R.id.etDateTime)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)

        loadClients {
            loadAppointmentData()
        }

        etDateTime.setOnClickListener {
            showDateTimePicker()
        }

        btnSave.setOnClickListener { updateAppointment() }
        btnCancel.setOnClickListener { dismiss() }

        return view
    }

    private fun loadClients(onComplete: () -> Unit) {
        db.collection("customers").get().addOnSuccessListener { documents ->
            allCustomers = documents.map { doc -> doc.toObject(Customer::class.java).copy(id = doc.id) }
            val names = allCustomers.map { it.fullname }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
            autoCompleteClient.setAdapter(adapter)

            autoCompleteClient.setOnItemClickListener { _, _, position, _ ->
                val name = adapter.getItem(position)
                selectedCustomer = allCustomers.find { it.fullname == name }
                autoCompleteClient.error = null // Limpiar error al seleccionar
            }

            autoCompleteClient.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    val name = s.toString().trim()
                    val match = allCustomers.find { it.fullname == name }
                    if (match != null) {
                        selectedCustomer = match
                        autoCompleteClient.error = null
                    } else {
                        selectedCustomer = null
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            autoCompleteClient.setOnClickListener {
                if (autoCompleteClient.adapter != null) autoCompleteClient.showDropDown()
            }

            autoCompleteClient.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) autoCompleteClient.showDropDown()
            }

            onComplete()
        }
    }

    private fun loadAppointmentData() {
        appointmentId?.let { id ->
            db.collection("appointments").document(id).get().addOnSuccessListener { doc ->
                val customerName = doc.getString("customerName") ?: ""
                val phone = doc.getString("customerPhone") ?: ""
                currentPhone = phone // Guardamos el teléfono actual

                autoCompleteClient.setText(customerName)
                selectedCustomer = allCustomers.find { it.fullname == customerName }

                val timestamp = doc.getTimestamp("date")
                appointmentDate = timestamp?.toDate()

                updateDateTimeEditText()
            }
        }
    }

    private fun updateDateTimeEditText() {
        appointmentDate?.let {
            val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale("en", "US"))
            etDateTime.setText(sdf.format(it))
        }
    }

    private fun showDateTimePicker() {
        val now = Calendar.getInstance()
        val calendar = Calendar.getInstance()
        appointmentDate?.let { calendar.time = it }

        DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)

            TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                val selected = calendar.time

                if (selected.before(now.time)) {
                    Toast.makeText(requireContext(), "You cannot select a past date or time", Toast.LENGTH_SHORT).show()
                    etDateTime.text.clear()
                    appointmentDate = null
                } else {
                    appointmentDate = selected
                    updateDateTimeEditText()
                    etDateTime.error = null
                }
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()

        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = now.timeInMillis
        }.show()
    }

    private fun updateAppointment() {
        val clientName = autoCompleteClient.text.toString().trim()

        if (clientName.isEmpty() || selectedCustomer == null) {
            autoCompleteClient.error = "Please select a client"
            return
        }

        if (appointmentDate == null) {
            etDateTime.error = "Please select date and time"
            return
        }

        val appointmentData = mapOf(
            "customerId" to selectedCustomer!!.id,
            "customerName" to selectedCustomer!!.fullname,
            "customerPhone" to selectedCustomer!!.phone.ifEmpty { currentPhone ?: "" },
            "date" to Timestamp(appointmentDate!!)
        )

        appointmentId?.let { id ->
            db.collection("appointments").document(id)
                .set(appointmentData, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Appointment updated successfully", Toast.LENGTH_SHORT).show()

                    val updatedAppointment = Appointment(
                        id = id,
                        customerId = selectedCustomer!!.id,
                        customerName = selectedCustomer!!.fullname,
                        customerPhone = selectedCustomer!!.phone.ifEmpty { currentPhone ?: "" },
                        date = appointmentDate!!,
                        status = "pendiente"
                    )
                    listener?.onAppointmentUpdated(updatedAppointment)
                    dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error updating appointment", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
