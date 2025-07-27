package com.example.workadministration.ui.appointment

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

    private lateinit var autoCompleteClient: AutoCompleteTextView
    private lateinit var etDate: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private val db = FirebaseFirestore.getInstance()
    private var allCustomers = listOf<Customer>()
    private var selectedCustomer: Customer? = null
    private var selectedDate: Date? = null

    private var listener: OnAppointmentAddedListener? = null

    fun setOnAppointmentAddedListener(listener: OnAppointmentAddedListener) {
        this.listener = listener
    }

    private fun checkCalendarPermissions(): Boolean {
        val readPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR)
        val writePermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_CALENDAR)

        if (readPermission != PackageManager.PERMISSION_GRANTED || writePermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                1001
            )
            return false
        }
        return true
    }

    // Manejo de resultado de permisos
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(requireContext(), "Calendar permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Calendar permissions denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
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
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    names
                )
                autoCompleteClient.setAdapter(adapter)

                autoCompleteClient.setOnItemClickListener { _, _, position, _ ->
                    val name = adapter.getItem(position)
                    selectedCustomer = allCustomers.find { it.fullname == name }
                    autoCompleteClient.error =
                        null // Limpiar el error al seleccionar un cliente válido
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
                    Toast.makeText(
                        requireContext(),
                        "You cannot select a past date or time",
                        Toast.LENGTH_SHORT
                    ).show()
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

        if (clientName.isEmpty() || selectedCustomer == null) {
            autoCompleteClient.error = "Please select a client"
            return
        }

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
                listener?.onAppointmentAdded(appointment)
                Toast.makeText(
                    requireContext(),
                    "Appointment added successfully",
                    Toast.LENGTH_SHORT
                ).show()
                addEventToCalendarAutomatically(
                    title = "Service - ${selectedCustomer!!.fullname}",
                    startDate = selectedDate!!,
                    address = selectedCustomer!!.address ?: "No address provided"
                )
                dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error saving appointment", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun addEventToCalendarAutomatically(title: String, startDate: Date, address: String) {
        if (!checkCalendarPermissions()) return

        val cr = requireContext().contentResolver

        try {
            // Obtener ID de calendario del usuario
            val calendarsCursor = cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.IS_PRIMARY} = 1",
                null,
                null
            )

            var calendarId: Long? = null
            calendarsCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    calendarId = cursor.getLong(0)
                }
            }

            if (calendarId == null) {
                Toast.makeText(requireContext(), "No calendar found", Toast.LENGTH_SHORT).show()
                return
            }

            val endDate = Date(startDate.time + 60 * 60 * 1000) // 1 hour later

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startDate.time)
                put(CalendarContract.Events.DTEND, endDate.time)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, "Created from WorkAdministration app")
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.EVENT_LOCATION, address)
            }

            val uri: Uri? = cr.insert(CalendarContract.Events.CONTENT_URI, values)

            if (uri != null) {
                Toast.makeText(requireContext(), "Event added to calendar", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to add event", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "No permission to access calendar", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error adding event: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
