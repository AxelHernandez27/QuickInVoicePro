package com.example.workadministration.ui.customer

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.workadministration.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddCustomerBottomSheet : BottomSheetDialogFragment() {

    private lateinit var listener: OnCustomerAddedListener

    interface OnCustomerAddedListener {
        fun onCustomerAdded(customer: Customer)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnCustomerAddedListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnCustomerAddedListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val view = LayoutInflater.from(context).inflate(R.layout.form_cliente_agregar, null)

        dialog.setContentView(view)

        val inputName = view.findViewById<EditText>(R.id.etFullName)
        val inputAddress = view.findViewById<EditText>(R.id.etAddress)
        val inputPhone = view.findViewById<EditText>(R.id.etPhone)
        val inputEmail = view.findViewById<EditText>(R.id.etEmail)
        val inputNotes = view.findViewById<EditText>(R.id.etNotes)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        btnCancel.setOnClickListener { dismiss() }

        btnSave.setOnClickListener {
            val name = inputName.text.toString().trim()
            val address = inputAddress.text.toString().trim()
            val phone = inputPhone.text.toString().trim()
            val email = inputEmail.text.toString().trim()
            val notes = inputNotes.text.toString().trim()

            // Validations
            if (name.isEmpty()) {
                inputName.error = "This field is required"
                return@setOnClickListener
            }
            if (address.isEmpty()) {
                inputAddress.error = "This field is required"
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                inputPhone.error = "This field is required"
                return@setOnClickListener
            }
            if (!phone.matches(Regex("^\\d{7,15}\$"))) {
                inputPhone.error = "Enter a valid phone number (digits only)"
                return@setOnClickListener
            }
            if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                inputEmail.error = "Enter a valid email"
                return@setOnClickListener
            }

            // Save directly, no confirmation dialog
            val customer = Customer(
                fullname = name,
                address = address,
                phone = phone,
                email = email,
                notes = notes
            )
            listener.onCustomerAdded(customer)

            Toast.makeText(requireContext(), "Customer added successfully", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        return dialog
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
