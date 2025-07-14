package com.example.workadministration.ui.invoice

import java.util.Date

data class Invoice (
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerAddress: String = "",
    val date: String = "",
    val extraCharges: Double = 0.0,
    val notes: String = "",
    val products: List<ProductDetail> = emptyList(),
    val total: Double = 0.0
)

data class ProductDetail(
    val productId: String = "",
    val name: String = "",
    val quantity: Number = 0,
    val price: Double = 0.0
)
