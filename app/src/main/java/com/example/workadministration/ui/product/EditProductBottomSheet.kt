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

class EditProductBottomSheet(
    private val product: Product
) : BottomSheetDialogFragment() {

    interface OnProductUpdatedListener {
        fun onProductUpdated(updatedProduct: Product)
    }

    private lateinit var listener: OnProductUpdatedListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnProductUpdatedListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnProductUpdatedListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val view = LayoutInflater.from(context).inflate(R.layout.form_producto_editar, null)

        dialog.setContentView(view)

        val inputName = view.findViewById<EditText>(R.id.etNombreProducto)
        val inputPrice = view.findViewById<EditText>(R.id.etPrecioProducto)
        val inputDescription = view.findViewById<EditText>(R.id.etDescripcionProducto)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelar)
        val btnUpdate = view.findViewById<Button>(R.id.btnGuardar)

        // Fill current product data
        inputName.setText(product.name)
        inputPrice.setText(product.price.toString())
        inputDescription.setText(product.description)

        btnCancel.setOnClickListener { dismiss() }

        btnUpdate.setOnClickListener {
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

            val updatedProduct = product.copy(
                name = name,
                price = price,
                description = description
            )

            listener.onProductUpdated(updatedProduct)
            Toast.makeText(requireContext(), "Product updated successfully", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        return dialog
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
