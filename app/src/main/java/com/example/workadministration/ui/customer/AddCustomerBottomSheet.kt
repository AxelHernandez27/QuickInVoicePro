package com.example.workadministration.ui.customer

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import com.example.workadministration.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddCustomerBottomSheet(
    private val listener: OnCustomerAddedListener
) : BottomSheetDialogFragment() {

    interface OnCustomerAddedListener {
        fun onCustomerAdded(customer: Customer)
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

        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        btnClose.setOnClickListener { dismiss() }

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
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val newCustomerRef = db.collection("customers").document()

            val customer = Customer(
                id = newCustomerRef.id,
                fullname = name,
                address = address,
                phone = phone,
                email = email,
                notes = notes
            )

            newCustomerRef.set(customer)
                .addOnSuccessListener {
                    listener.onCustomerAdded(customer)
                    Toast.makeText(requireContext(), "Customer added successfully", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error saving customer", Toast.LENGTH_SHORT).show()
                }

        }

        return dialog
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
