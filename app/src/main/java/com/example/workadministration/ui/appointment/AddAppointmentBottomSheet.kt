package com.example.workadministration.ui.appointment

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.workadministration.R
import com.example.workadministration.ui.customer.Customer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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

    private var accessToken: String? = null

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
                    autoCompleteClient.error = null
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
            datePicker.minDate = now.timeInMillis
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

        val title = "Service - ${selectedCustomer!!.fullname}"
        val address = selectedCustomer!!.address?.replace("\n", " ")?.trim() ?: "No address provided"

        val accessToken = requireContext()
            .getSharedPreferences("my_prefs", android.content.Context.MODE_PRIVATE)
            .getString("ACCESS_TOKEN", null)

        Log.d("Add Appointment", "Token: $accessToken")

        btnSave.isEnabled = false

        lifecycleScope.launch {
            val eventId = if (accessToken != null) {
                withContext(Dispatchers.IO) {
                    Log.d("Callieng add", "Token: $accessToken")
                    addEventToGoogleCalendar(accessToken, title, selectedDate!!, address)
                }
            } else null

            val appointmentData = hashMapOf(
                "customerId" to selectedCustomer!!.id,
                "customerName" to selectedCustomer!!.fullname,
                "customerPhone" to selectedCustomer!!.phone,
                "date" to Timestamp(selectedDate!!),
                "status" to "pendiente",
                "eventId" to eventId
            )

            db.collection("appointments").add(appointmentData)
                .addOnSuccessListener { docRef ->
                    val appointment = Appointment(
                        id = docRef.id,
                        customerId = selectedCustomer!!.id,
                        customerName = selectedCustomer!!.fullname,
                        customerPhone = selectedCustomer!!.phone,
                        date = selectedDate!!,
                        status = "pendiente",
                        eventId = eventId
                    )
                    listener?.onAppointmentAdded(appointment)
                    Toast.makeText(requireContext(), "Appointment added successfully", Toast.LENGTH_SHORT).show()
                    Log.d("CalendarAPI", "saveAppointment called, accessToken = $accessToken")
                    dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error saving appointment", Toast.LENGTH_SHORT).show()
                }

            btnSave.isEnabled = true
        }
    }

    private suspend fun addEventToGoogleCalendar(
        accessToken: String,
        title: String,
        startDate: Date,
        address: String
    ): String? {
        return withContext(Dispatchers.IO) {
            val endDate = Date(startDate.time + 60 * 60 * 1000) // 1 hora duración
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            isoFormat.timeZone = TimeZone.getTimeZone("UTC")

            Log.d("AddAppointment", "Start date: ${startDate}")
            Log.d("AddAppointment", "End date: $endDate")
            Log.d("AddAppointment", "Title: $title")
            Log.d("AddAppointment", "Address: $address")
            Log.d("AddAppointment", "TimeZone: ${TimeZone.getDefault().id}")

            val json = JSONObject().apply {
                put("summary", title)
                put("description", "Created from Invozo app")
                put("start", JSONObject().apply {
                    put("dateTime", isoFormat.format(startDate))
                    put("timeZone", "UTC")
                })
                put("end", JSONObject().apply {
                    put("dateTime", isoFormat.format(endDate))
                    put("timeZone", "UTC")
                })
                put("location", address)
            }


            Log.d("CalendarAPI", "Sending JSON: $json")

            try {
                val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json")
                }

                connection.outputStream.use {
                    it.write(json.toString().toByteArray(Charsets.UTF_8))
                }


                val responseCode = connection.responseCode
                val responseText = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                }

                Log.d("CalendarAPI", "Response code: $responseCode, Response body: $responseText")

                if (responseCode >= 400) {
                    Log.e("CalendarAPI", "Bad request. Body: $responseText")
                }


                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    val jsonResponse = org.json.JSONObject(responseText)
                    val eventId = jsonResponse.optString("id", null)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Event added to Google Calendar", Toast.LENGTH_SHORT).show()
                    }
                    eventId

                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to add event: $responseText", Toast.LENGTH_LONG).show()
                    }
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }

                null
            }
        }
    }



    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}