package com.example.workadministration.ui.customer

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.R
import com.google.firebase.firestore.FirebaseFirestore

class EditCustomerActivity : AppCompatActivity() {

    private lateinit var etFullName: EditText
    private lateinit var etAddress: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etNotes: EditText
    private lateinit var btnCancel: Button
    private lateinit var btnUpdate: Button

    private lateinit var customerId: String
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.form_cliente_editar)

        // Inicializa vistas
        etFullName = findViewById(R.id.etFullName)
        etAddress = findViewById(R.id.etAddress)
        etPhone = findViewById(R.id.etPhone)
        etEmail = findViewById(R.id.etEmail)
        etNotes = findViewById(R.id.etNotes)
        btnCancel = findViewById(R.id.btnCancel)
        btnUpdate = findViewById(R.id.btnUpdate)

        // Obtén datos del Intent
        val intent = intent
        customerId = intent.getStringExtra("id") ?: return
        etFullName.setText(intent.getStringExtra("fullname"))
        etAddress.setText(intent.getStringExtra("address"))
        etPhone.setText(intent.getStringExtra("phone"))
        etEmail.setText(intent.getStringExtra("email"))
        etNotes.setText(intent.getStringExtra("notes"))

        btnCancel.setOnClickListener {
            finish()
        }

        btnUpdate.setOnClickListener {
            val updatedCustomer = hashMapOf(
                "fullname" to etFullName.text.toString(),
                "address" to etAddress.text.toString(),
                "phone" to etPhone.text.toString(),
                "email" to etEmail.text.toString(),
                "notes" to etNotes.text.toString()
            )

            db.collection("customers").document(customerId)
                .update(updatedCustomer as Map<String, Any>)
                .addOnSuccessListener {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error updating customer", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
