package com.example.workadministration.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.R
import com.example.workadministration.ui.invoice.Invoice
import com.example.workadministration.ui.invoice.ProductDetail
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.example.workadministration.ui.NavigationUtil


class DashboardActivity: AppCompatActivity()  {

    private lateinit var pieChartTickets: PieChart
    private lateinit var barChartMensual: BarChart
    private lateinit var pieChartAnual: PieChart

    private val db = FirebaseFirestore.getInstance()
    private val invoiceList = mutableListOf<Invoice>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        setContentView(R.layout.activity_dashboard) // asegúrate que exista

        // Vincular navegación inferior
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        NavigationUtil.setupNavigation(this, bottomNav, R.id.nav_dashboard)

        pieChartTickets = findViewById(R.id.pieChartTickets)
        barChartMensual = findViewById(R.id.barChartMensual)
        pieChartAnual = findViewById(R.id.pieChartAnual)
        loadInvoices()
    }

    private fun loadInvoices() {
        invoiceList.clear()

        val displayFormat = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US)
        displayFormat.timeZone = TimeZone.getTimeZone("America/Mexico_City")

        db.collection("invoices")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    updateCharts()
                    return@addOnSuccessListener
                }

                for (doc in snapshot.documents) {
                    val invoiceId = doc.id
                    val customerId = doc.getString("customerId") ?: ""
                    val customerName = doc.getString("customerName") ?: ""
                    val customerAddress = doc.getString("customerAddress") ?: ""
                    val dateField = doc.get("date")
                    val dateValue = if (dateField is com.google.firebase.Timestamp) dateField.toDate() else Date()
                    val extraCharges = doc.getDouble("extraCharges") ?: 0.0
                    val notes = doc.getString("notes") ?: ""
                    val total = doc.getDouble("total") ?: 0.0

                    db.collection("invoices").document(invoiceId)
                        .collection("invoiceDetails")
                        .get()
                        .addOnSuccessListener { detailsSnapshot ->
                            val products = detailsSnapshot.map { detailDoc ->
                                ProductDetail(
                                    productId = detailDoc.getString("productId") ?: "",
                                    name = detailDoc.getString("productName") ?: "",
                                    price = detailDoc.getDouble("price") ?: 0.0
                                )
                            }

                            val invoice = Invoice(
                                id = invoiceId,
                                customerId = customerId,
                                customerName = customerName,
                                customerAddress = customerAddress,
                                date = displayFormat.format(dateValue),
                                extraCharges = extraCharges,
                                notes = notes,
                                total = total,
                                products = products
                            )

                            invoiceList.add(invoice)

                            // Cuando terminamos de cargar todos, actualizamos las gráficas
                            if (invoiceList.size == snapshot.size()) {
                                updateCharts()
                            }
                        }
                }
            }
    }

    private fun updateCharts() {
        updatePieChartTickets()
        updateBarChartMensual()
        updatePieChartAnual()
    }

    private fun updatePieChartTickets() {
        val totalTickets = invoiceList.sumOf { it.total }.toFloat()
        val gastoMateriales = invoiceList.sumOf { it.products.sumOf { p -> p.price } }.toFloat()
        val ganancias = totalTickets - gastoMateriales

        val entries = listOf(
            PieEntry(totalTickets, "Total Tickets"),
            PieEntry(gastoMateriales, "Gasto Materiales"),
            PieEntry(ganancias, "Ganancias")
        )

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(Color.BLUE, Color.RED, Color.GREEN)
        val data = PieData(dataSet)
        data.setValueTextColor(Color.WHITE)
        data.setValueTextSize(14f)

        pieChartTickets.data = data
        pieChartTickets.centerText = "Resumen Tickets"
        pieChartTickets.description.isEnabled = false
        pieChartTickets.animateY(1000)
        pieChartTickets.invalidate()
    }

    private fun updateBarChartMensual() {
        val monthlyMap = mutableMapOf<Int, Float>() // mes -> total
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US)

        for (invoice in invoiceList) {
            val date = dateFormat.parse(invoice.date) ?: continue
            calendar.time = date
            val month = calendar.get(Calendar.MONTH)
            monthlyMap[month] = monthlyMap.getOrDefault(month, 0f) + invoice.total.toFloat()
        }

        val entries = monthlyMap.entries.sortedBy { it.key }.map { BarEntry(it.key.toFloat(), it.value) }
        val dataSet = BarDataSet(entries, "Ingresos Mensuales")
        dataSet.color = Color.BLUE
        val data = BarData(dataSet)
        data.barWidth = 0.9f

        barChartMensual.data = data
        barChartMensual.setFitBars(true)
        barChartMensual.description.isEnabled = false
        barChartMensual.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChartMensual.xAxis.granularity = 1f
        barChartMensual.xAxis.valueFormatter = MonthValueFormatter()
        barChartMensual.axisRight.isEnabled = false
        barChartMensual.invalidate()
    }

    private fun updatePieChartAnual() {
        val totalTickets = invoiceList.sumOf { it.total }.toFloat()
        val gastoMateriales = invoiceList.sumOf { it.products.sumOf { p -> p.price } }.toFloat()
        val ganancias = totalTickets - gastoMateriales

        val entries = listOf(
            PieEntry(totalTickets, "Total Tickets"),
            PieEntry(gastoMateriales, "Gasto Materiales"),
            PieEntry(ganancias, "Ganancias")
        )

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(Color.BLUE, Color.RED, Color.GREEN)
        val data = PieData(dataSet)
        data.setValueTextColor(Color.WHITE)
        data.setValueTextSize(14f)

        pieChartAnual.data = data
        pieChartAnual.centerText = "Resumen Anual"
        pieChartAnual.description.isEnabled = false
        pieChartAnual.animateY(1000)
        pieChartAnual.invalidate()
    }
}

// Formatter para los meses en el BarChart
class MonthValueFormatter : com.github.mikephil.charting.formatter.ValueFormatter() {
    private val months = arrayOf(
        "Ene","Feb","Mar","Abr","May","Jun",
        "Jul","Ago","Sep","Oct","Nov","Dic"
    )
    override fun getFormattedValue(value: Float): String {
        val index = value.toInt()
        return if (index in 0..11) months[index] else value.toString()
    }
}