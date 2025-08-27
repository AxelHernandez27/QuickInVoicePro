package com.example.workadministration.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.workadministration.R
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.example.workadministration.ui.NavigationUtil
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var pieChartTickets: PieChart
    private lateinit var pieChartAnual: PieChart

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Vincular navegación inferior
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        NavigationUtil.setupNavigation(this, bottomNav, R.id.nav_dashboard)

        pieChartTickets = findViewById(R.id.pieChartTickets)
        pieChartAnual = findViewById(R.id.pieChartAnual)

        loadMonthlyReports { reports ->
            if (reports.isNotEmpty()) {
                // Usamos el último mes para el pieChartTickets
                val lastMonthReport = reports.maxByOrNull { it.year * 100 + it.month }!!
                updatePieChartTickets(lastMonthReport)
            }
            // Para anual, sumamos todos los meses
            val annualReport = reports.groupBy { it.year }
                .mapValues { entry ->
                    val totalTickets = entry.value.sumOf { it.totalTickets }
                    val totalMaterials = entry.value.sumOf { it.totalMaterials }
                    val profit = entry.value.sumOf { it.profit }
                    MonthlyReport(entry.key, 0, totalTickets, totalMaterials, profit)
                }
            if (annualReport.isNotEmpty()) {
                val thisYear = Calendar.getInstance().get(Calendar.YEAR)
                annualReport[thisYear]?.let { updatePieChartAnual(it) }
            }
        }
    }

    private fun loadMonthlyReports(onComplete: (List<MonthlyReport>) -> Unit) {
        db.collection("reports")
            .get()
            .addOnSuccessListener { snapshot ->
                val reports = snapshot.mapNotNull { doc ->
                    val year = doc.getLong("year")?.toInt() ?: return@mapNotNull null
                    val month = doc.getLong("month")?.toInt() ?: return@mapNotNull null
                    val totalTickets = doc.getDouble("totalTickets") ?: 0.0
                    val totalMaterials = doc.getDouble("totalMaterials") ?: 0.0
                    val profit = doc.getDouble("profit") ?: 0.0
                    MonthlyReport(year, month, totalTickets, totalMaterials, profit)
                }.sortedWith(compareBy({ it.year }, { it.month }))
                onComplete(reports)
            }
    }

    private fun updatePieChartTickets(report: MonthlyReport) {
        val entries = listOf(
            PieEntry(report.totalTickets.toFloat(), "Total Tickets"),
            PieEntry(report.totalMaterials.toFloat(), "Gasto Materiales"),
            PieEntry(report.profit.toFloat(), "Ganancias")
        )

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(Color.BLUE, Color.RED, Color.GREEN)
        val data = PieData(dataSet)
        data.setValueTextColor(Color.WHITE)
        data.setValueTextSize(14f)

        pieChartTickets.data = data
        pieChartTickets.centerText = "Resumen Último Mes"
        pieChartTickets.description.isEnabled = false
        pieChartTickets.animateY(1000)
        pieChartTickets.invalidate()
    }

    private fun updatePieChartAnual(report: MonthlyReport) {
        val entries = listOf(
            PieEntry(report.totalTickets.toFloat(), "Total Tickets"),
            PieEntry(report.totalMaterials.toFloat(), "Gasto Materiales"),
            PieEntry(report.profit.toFloat(), "Ganancias")
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

data class MonthlyReport(
    val year: Int,
    val month: Int,
    val totalTickets: Double,
    val totalMaterials: Double,
    val profit: Double
)
