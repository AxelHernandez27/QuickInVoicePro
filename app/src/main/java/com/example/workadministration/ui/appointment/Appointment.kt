package com.example.workadministration.ui.appointment

import java.util.Date

data class Appointment(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val date: Date = Date(),
    val notes: String = "",
    val status: String = "pendiente",
    val customerPhone: String = ""
)
