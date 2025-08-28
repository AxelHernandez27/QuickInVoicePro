package com.example.workadministration.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
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
    private lateinit var spMes: Spinner
    private lateinit var spAnio: Spinner

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Vincular navegación inferior
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        NavigationUtil.setupNavigation(this, bottomNav, R.id.nav_dashboard)

        pieChartTickets = findViewById(R.id.pieChartTickets)
        pieChartAnual = findViewById(R.id.pieChartAnual)
        spMes = findViewById(R.id.spMes)
        spAnio = findViewById(R.id.spAnio)

        loadMonthlyReports { reports ->
            if (reports.isNotEmpty()) {
                setupSpinners(reports)
                setupListeners(reports)

                // Selecciona por defecto el último mes disponible
                val lastReport = reports.maxByOrNull { it.year * 100 + it.month }!!
                spAnio.setSelection((spAnio.adapter as ArrayAdapter<Int>).getPosition(lastReport.year))
                spMes.setSelection(lastReport.month - 1)
                updatePieChartTickets(lastReport)

                // Actualizar gráfica anual por defecto
                val yearReports = reports.filter { it.year == lastReport.year }
                val totalTickets = yearReports.sumOf { it.totalTickets }
                val totalMaterials = yearReports.sumOf { it.totalMaterials }
                val profit = yearReports.sumOf { it.profit }
                updatePieChartAnual(MonthlyReport(lastReport.year, 0, totalTickets, totalMaterials, profit))

            }
        }



    }

    private fun setupSpinners(reports: List<MonthlyReport>) {
        val meses = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        val adapterMes = ArrayAdapter(this, android.R.layout.simple_spinner_item, meses)
        adapterMes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spMes.adapter = adapterMes

        // Sacamos años únicos de los reportes
        val years = reports.map { it.year }.distinct().sorted()
        val adapterAnio = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)
        adapterAnio.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spAnio.adapter = adapterAnio
    }

    private fun setupListeners(reports: List<MonthlyReport>) {
        var isMesInitialized = false
        var isAnioInitialized = false

        // Listener del spinner mensual
        spMes.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (!isMesInitialized) {
                    isMesInitialized = true
                    return
                }
                val selectedYear = spAnio.selectedItem as Int
                val selectedMonthIndex = position + 1
                val monthlyReport = reports.find { it.year == selectedYear && it.month == selectedMonthIndex }

                if (monthlyReport != null) {
                    updatePieChartTickets(monthlyReport)
                } else {
                    // Mostrar mensaje en inglés si no hay datos
                    android.widget.Toast.makeText(
                        this@DashboardActivity,
                        "No data available for this month",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()

                    // Limpiar o vaciar la gráfica mensual si quieres
                    pieChartTickets.clear()
                    pieChartTickets.centerText = "No data"
                    pieChartTickets.invalidate()
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // Listener del spinner anual
        spAnio.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (!isAnioInitialized) {
                    isAnioInitialized = true
                    return
                }
                val selectedYear = spAnio.selectedItem as Int
                val yearReports = reports.filter { it.year == selectedYear }
                if (yearReports.isNotEmpty()) {
                    val totalTickets = yearReports.sumOf { it.totalTickets }
                    val totalMaterials = yearReports.sumOf { it.totalMaterials }
                    val profit = yearReports.sumOf { it.profit }
                    updatePieChartAnual(MonthlyReport(selectedYear, 0, totalTickets, totalMaterials, profit))
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
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
            PieEntry(report.totalMaterials.toFloat(), "Material Expenses"),
            PieEntry(report.profit.toFloat(), "Earnings")
        )

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(Color.BLUE, Color.RED, Color.GREEN)
        val data = PieData(dataSet)
        data.setValueTextColor(Color.WHITE)
        data.setValueTextSize(14f)

        pieChartTickets.data = data
        pieChartTickets.centerText = "Summary Last Month"
        pieChartTickets.description.isEnabled = false
        pieChartTickets.animateY(1000)
        pieChartTickets.invalidate()
    }

    private fun updatePieChartAnual(report: MonthlyReport) {
        val entries = listOf(
            PieEntry(report.totalTickets.toFloat(), "Total Tickets"),
            PieEntry(report.totalMaterials.toFloat(), "Material Expenses"),
            PieEntry(report.profit.toFloat(), "Earnings")
        )

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(Color.BLUE, Color.RED, Color.GREEN)
        val data = PieData(dataSet)
        data.setValueTextColor(Color.WHITE)
        data.setValueTextSize(14f)

        pieChartAnual.data = data
        pieChartAnual.centerText = "Annual Summary"
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
