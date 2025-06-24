package com.example.workadministration.ui.product

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workadministration.R
import com.example.workadministration.ui.NavigationUtil
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class ProductActivity : AppCompatActivity() {

    private lateinit var recyclerProducts: RecyclerView
    private lateinit var searchProduct: EditText
    private lateinit var adapter: ProductAdapter
    private val productsList = mutableListOf<Product>()
    private val db = FirebaseFirestore.getInstance()

    private val addProductLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            getProducts()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_products)

        val buttonAdd = findViewById<Button>(R.id.btnAgregarProducto)
        buttonAdd.setOnClickListener {
            val intent = Intent(this, AddProductActivity::class.java)
            addProductLauncher.launch(intent)
        }

        recyclerProducts = findViewById(R.id.recyclerProductos)
        searchProduct = findViewById(R.id.buscarProducto)

        adapter = ProductAdapter(productsList, { product ->
            deleteProduct(product)
        }, { product ->
            val intent = Intent(this, EditProductActivity::class.java).apply {
                putExtra("id", product.id)
                putExtra("name", product.name)
                putExtra("price", product.price)
                putExtra("description", product.description)
                putExtra("category", product.category)
            }
            addProductLauncher.launch(intent)
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

        // Integración de la navegación
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        NavigationUtil.setupNavigation(this, bottomNav, R.id.nav_products)
    }

    private fun getProducts() {
        db.collection("products")
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

    private fun deleteProduct(product: Product) {
        db.collection("products").document(product.id)
            .delete()
            .addOnSuccessListener {
                productsList.remove(product)
                adapter.updateList(productsList)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting product", Toast.LENGTH_SHORT).show()
            }
    }
}