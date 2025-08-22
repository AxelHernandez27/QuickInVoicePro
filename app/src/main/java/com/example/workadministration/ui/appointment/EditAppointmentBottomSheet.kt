package com.example.workadministration.ui.appointment

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.workadministration.R
import com.example.workadministration.TokenHelper
import com.example.workadministration.ui.customer.Customer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    private var eventId: String? = null

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

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
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

        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        btnClose.setOnClickListener { dismiss() }

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
                val event = doc.getString("eventId")
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

        val context = requireContext()
        //val sharedPref = context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
        lifecycleScope.launch {
            val accessToken = TokenHelper.refreshGoogleToken(requireContext())

            if (accessToken.isNullOrEmpty()) {
                Toast.makeText(context, "No valid Google token available", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val updatedPhone = selectedCustomer!!.phone.ifEmpty { currentPhone ?: "" }
            val appointmentData = mapOf(
                "customerId" to selectedCustomer!!.id,
                "customerName" to selectedCustomer!!.fullname,
                "customerPhone" to updatedPhone,
                "date" to Timestamp(appointmentDate!!),
                "eventId" to eventId
            )

            appointmentId?.let { id ->
                db.collection("appointments").document(id)
                    .get()
                    .addOnSuccessListener { doc ->
                        val eventId = doc.getString("eventId")

                        db.collection("appointments").document(id)
                            .set(appointmentData, SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(context, "Appointment updated successfully", Toast.LENGTH_SHORT).show()

                                val updatedAppointment = Appointment(
                                    id = id,
                                    customerId = selectedCustomer!!.id,
                                    customerName = selectedCustomer!!.fullname,
                                    customerPhone = updatedPhone,
                                    date = appointmentDate!!,
                                    eventId = eventId
                                )
                                listener?.onAppointmentUpdated(updatedAppointment)

                                if (!eventId.isNullOrEmpty()) {
                                    val title = "Service - ${selectedCustomer!!.fullname}"
                                    val address = selectedCustomer!!.address

                                    lifecycleScope.launch {
                                        val success = updateEventOnGoogleCalendar(
                                            accessToken,
                                            eventId,
                                            title,
                                            appointmentDate!!,
                                            address
                                        )
                                        if (success) {
                                            Toast.makeText(context, "Google Calendar event updated", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed to update Google Calendar event", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                dismiss()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Error updating appointment", Toast.LENGTH_SHORT).show()
                            }
                    }
            }
        }

    }


    private suspend fun updateEventOnGoogleCalendar(
        accessToken: String,
        eventId: String,
        title: String,
        startDate: Date,
        address: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val endDate = Date(startDate.time + 60 * 60 * 1000)
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            isoFormat.timeZone = TimeZone.getTimeZone("UTC")

            val json = JSONObject().apply {
                put("summary", title)
                put("description", "Updated from Invozo app")
                put("start", JSONObject().apply {
                    put("dateTime", isoFormat.format(startDate))
                    put("timeZone", "UTC")
                })
                put("end", JSONObject().apply {
                    put("dateTime", isoFormat.format(endDate))
                    put("timeZone", "UTC")
                })
                put("location", address.replace("\n", " ").trim())
            }.toString()

            Log.d("CalendarAPI", "JSON payload: $json")


            try {
                val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events/$eventId")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PATCH"
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json")
                }

                connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }

                val responseCode = connection.responseCode
                val responseText = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                }

                Log.d("CalendarAPI", "Update Response code: $responseCode, body: $responseText")

                responseCode in 200..299
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error updating event: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                false
            }
        }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
