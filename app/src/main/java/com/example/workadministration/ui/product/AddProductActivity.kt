package com.example.workadministration.ui.product

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.R
import com.google.firebase.firestore.FirebaseFirestore

class AddProductActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPrice: EditText
    private lateinit var etDescription: EditText
    //private lateinit var etCategory: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.form_producto_agregar)

        etName = findViewById(R.id.etNombreProducto)
        etPrice = findViewById(R.id.etPrecioProducto)
        etDescription = findViewById(R.id.etDescripcionProducto)
        //etCategory = findViewById(R.id.etCategoriaProducto)
        btnSave = findViewById(R.id.btnGuardar)
        btnCancel = findViewById(R.id.btnCancelar)

        btnSave.setOnClickListener {
            saveProduct()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveProduct() {
        val name = etName.text.toString().trim()
        val priceText = etPrice.text.toString().trim()
        val price = priceText.toDoubleOrNull()
        val description = etDescription.text.toString().trim()
        //val category = etCategory.text.toString().trim()

        if(name.isEmpty()) {
            etName.error = "Name required"
            etName.requestFocus()
            return
        }

        val product = hashMapOf(
            "name" to name,
            "price" to price,
            "description" to description,
            //"category" to category
        )

        db.collection("products")
            .add(product)
            .addOnSuccessListener {
                Toast.makeText(this, "Product added", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error saving product", Toast.LENGTH_SHORT).show()
            }

    }
}