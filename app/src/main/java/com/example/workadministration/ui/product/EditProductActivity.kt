package com.example.workadministration.ui.product

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.R
import com.google.firebase.firestore.FirebaseFirestore

class EditProductActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPrice: EditText
    private lateinit var etDescription: EditText
    //private lateinit var etCategory: EditText
    private lateinit var btnUpdate: Button
    private lateinit var btnCancel: Button

    private lateinit var productId: String
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.form_producto_editar)

        etName = findViewById(R.id.etNombreProducto)
        etPrice = findViewById(R.id.etPrecioProducto)
        etDescription = findViewById(R.id.etDescripcionProducto)
        //etCategory = findViewById(R.id.etCategoriaProducto)
        btnUpdate = findViewById(R.id.btnGuardar)
        btnCancel = findViewById(R.id.btnCancelar)

        val intent = intent
        productId = intent.getStringExtra("id") ?: return
        etName.setText(intent.getStringExtra("name"))
        etPrice.setText(intent.getDoubleExtra("price", 0.0).toString())
        etDescription.setText(intent.getStringExtra("description"))
        //etCategory.setText(intent.getStringExtra("category"))

        btnCancel.setOnClickListener {
            finish()
        }

        btnUpdate.setOnClickListener {
            updateProduct()
        }
    }

    private fun updateProduct() {
        val name = etName.text.toString().trim()
        val priceText = etPrice.text.toString().trim()
        val description = etDescription.text.toString().trim()
        // val category = etCategory.text.toString().trim()

        if(name.isEmpty()) {
            etName.error = "Name required"
            etName.requestFocus()
            return
        }

        val price = priceText.toDoubleOrNull()
        if (price == null) {
            etPrice.error = "Enter a valid price"
            etPrice.requestFocus()
            return
        }

        val updateProduct = hashMapOf(
            "name" to name,
            "price" to price,
            "description" to description,
            //"category" to category
        )

        db.collection("products").document(productId)
            .update(updateProduct as Map<String, Any>)
            .addOnSuccessListener {
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error updating product", Toast.LENGTH_SHORT).show()
            }
    }
}