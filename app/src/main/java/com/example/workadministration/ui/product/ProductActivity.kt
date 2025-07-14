package com.example.workadministration.ui.product

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.example.workadministration.ui.NavigationUtil
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class ProductActivity : AppCompatActivity(),
    AddProductBottomSheet.OnProductAddedListener,
    EditProductBottomSheet.OnProductUpdatedListener {

    private lateinit var recyclerProducts: RecyclerView
    private lateinit var searchProduct: EditText
    private lateinit var adapter: ProductAdapter
    private val productsList = mutableListOf<Product>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_products)

        val buttonAdd = findViewById<Button>(R.id.btnAgregarProducto)
        buttonAdd.setOnClickListener {
            val bottomSheet = AddProductBottomSheet()
            bottomSheet.show(supportFragmentManager, "AddProductBottomSheet")
        }

        recyclerProducts = findViewById(R.id.recyclerProductos)
        searchProduct = findViewById(R.id.buscarProducto)

        adapter = ProductAdapter(productsList, { product ->
            confirmDeleteProduct(product)
        }, { product ->
            val editSheet = EditProductBottomSheet(product)
            editSheet.show(supportFragmentManager, "EditProductBottomSheet")
        })

        recyclerProducts.layoutManager = LinearLayoutManager(this)
        recyclerProducts.adapter = adapter

        getProducts()

        searchProduct.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterProducts(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        NavigationUtil.setupNavigation(this, bottomNav, R.id.nav_products)
    }

    private fun getProducts() {
        db.collection("products")
            .orderBy("name", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                productsList.clear()
                for (document in documents) {
                    val product = document.toObject(Product::class.java).copy(id = document.id)
                    productsList.add(product)
                }
                adapter.updateList(productsList)
            }
    }

    private fun filterProducts(text: String) {
        val filteredList = productsList.filter {
            it.name.contains(text, ignoreCase = true) ||
                    it.description.contains(text, ignoreCase = true)
        }
        adapter.updateList(filteredList)
    }

    private fun confirmDeleteProduct(product: Product) {
        AlertDialog.Builder(this)
            .setTitle("Confirmation")
            .setMessage("Do you want to delete ${product.name}?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("products").document(product.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Product deleted", Toast.LENGTH_SHORT).show()
                        productsList.remove(product)
                        adapter.updateList(productsList)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error deleting product", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Listener for adding new product
    override fun onProductAdded(product: Product) {
        db.collection("products")
            .add(product)
            .addOnSuccessListener {
                Toast.makeText(this, "Product added successfully", Toast.LENGTH_SHORT).show()
                getProducts()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error adding product", Toast.LENGTH_SHORT).show()
            }
    }

    // Listener for updating existing product
    override fun onProductUpdated(updatedProduct: Product) {
        db.collection("products").document(updatedProduct.id)
            .set(updatedProduct)
            .addOnSuccessListener {
                Toast.makeText(this, "Product updated successfully", Toast.LENGTH_SHORT).show()
                getProducts()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error updating product", Toast.LENGTH_SHORT).show()
            }
    }
}
