package com.example.workadministration.ui.product

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.workadministration.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddProductBottomSheet : BottomSheetDialogFragment() {

    interface OnProductAddedListener {
        fun onProductAdded(product: Product)
    }

    private lateinit var listener: OnProductAddedListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnProductAddedListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnProductAddedListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val view = LayoutInflater.from(context).inflate(R.layout.form_producto_agregar, null)

        dialog.setContentView(view)

        val inputName = view.findViewById<EditText>(R.id.etNombreProducto)
        val inputPrice = view.findViewById<EditText>(R.id.etPrecioProducto)
        val inputDescription = view.findViewById<EditText>(R.id.etDescripcionProducto)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelar)
        val btnSave = view.findViewById<Button>(R.id.btnGuardar)

        btnCancel.setOnClickListener { dismiss() }

        btnSave.setOnClickListener {
            val name = inputName.text.toString().trim()
            val priceText = inputPrice.text.toString().trim()
            val description = inputDescription.text.toString().trim()

            // Validations
            if (name.isEmpty()) {
                inputName.error = "Required field"
                return@setOnClickListener
            }
            if (priceText.isEmpty()) {
                inputPrice.error = "Required field"
                return@setOnClickListener
            }

            val price = priceText.toDoubleOrNull()
            if (price == null || price < 0) {
                inputPrice.error = "Enter a valid price"
                return@setOnClickListener
            }

            val product = Product(
                id = "",
                name = name,
                price = price,
                description = description
            )

            listener.onProductAdded(product)
            Toast.makeText(requireContext(), "Product added successfully", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        return dialog
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
