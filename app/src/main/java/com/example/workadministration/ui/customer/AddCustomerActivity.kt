package com.example.workadministration.ui.customer

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.R
import com.google.firebase.firestore.FirebaseFirestore

class AddCustomerActivity : AppCompatActivity() {

    private lateinit var etFullName: EditText
    private lateinit var etAddress: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etNotes: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.form_cliente_agregar) // Asegúrate que este es el nombre del layout

        etFullName = findViewById(R.id.etFullName)
        etAddress = findViewById(R.id.etAddress)
        etPhone = findViewById(R.id.etPhone)
        etEmail = findViewById(R.id.etEmail)
        etNotes = findViewById(R.id.etNotes)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        btnSave.setOnClickListener {
            saveCustomer()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveCustomer() {
        val name = etFullName.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val notes = etNotes.text.toString().trim()

        if (name.isEmpty()) {
            etFullName.error = "Name required"
            etFullName.requestFocus()
            return
        }

        val customer = hashMapOf(
            "fullname" to name,
            "address" to address,
            "phone" to phone,
            "email" to email,
            "notes" to notes
        )

        db.collection("customers")
            .add(customer)
            .addOnSuccessListener {
                Toast.makeText(this, "Customer added", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK) // ✅ <- Esto informa que todo salió bien
                finish()

            }
            .addOnFailureListener {
                Toast.makeText(this, "Error saving customer", Toast.LENGTH_SHORT).show()
            }
    }
}
