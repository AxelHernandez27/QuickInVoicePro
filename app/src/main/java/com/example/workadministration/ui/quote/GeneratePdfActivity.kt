package com.example.workadministration.ui.quote

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.workadministration.R
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class GeneratePdfActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var quoteId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quoteId = intent.getStringExtra("quoteId")

        if (quoteId.isNullOrEmpty()) {
            Toast.makeText(this, "Quote ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fetchQuoteData()
    }

    private fun fetchQuoteData() {
        db.collection("quotes").document(quoteId!!)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val customerId = doc.getString("customerId") ?: ""
                    val total = doc.getDouble("total") ?: 0.0
                    val notes = doc.getString("notes") ?: ""
                    val extra = doc.getDouble("extraCharges") ?: 0.0
                    val date = doc.getTimestamp("date")?.toDate() ?: Date()

                    db.collection("customers").document(customerId)
                        .get()
                        .addOnSuccessListener { customerDoc ->
                            val customerName = customerDoc.getString("fullname") ?: ""
                            val customerAddress = customerDoc.getString("address") ?: ""
                            val customerPhone = customerDoc.getString("phone") ?: ""
                            val customerEmail = customerDoc.getString("email") ?: ""

                            db.collection("quotes").document(quoteId!!)
                                .collection("quoteDetails")
                                .get()
                                .addOnSuccessListener { details ->
                                    val products = details.map {
                                        val name = it.getString("name") ?: ""
                                        val price = it.getDouble("price") ?: 0.0
                                        val quantity = it.getLong("quantity")?.toInt() ?: 1
                                        Triple(name, price, quantity)
                                    }
                                    generatePDF(customerName, customerAddress, customerPhone, customerEmail, date, total, notes, extra, products)
                                }
                        }
                } else {
                    Toast.makeText(this, "Quote not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching quote", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun generatePDF(customerName: String, customerAddress: String, customerPhone: String, customerEmail: String, date: Date, total: Double, notes: String, extra: Double, products: List<Triple<String, Double, Int>>) {

        val pdfDocument = PdfDocument()
        val pageWidth = 400
        val pageHeight = 750 + (products.size * 35)
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        val logo = BitmapFactory.decodeResource(resources, R.drawable.logo1)
        val scaledWatermark = Bitmap.createScaledBitmap(logo, 300, 300, false)

        val watermarkPaint = Paint()
        watermarkPaint.alpha = 30

        val watermarkX = (pageWidth - scaledWatermark.width) / 2f
        val watermarkY = (pageHeight - scaledWatermark.height) / 2f

        canvas.drawBitmap(scaledWatermark, watermarkX, watermarkY, watermarkPaint)
        var yPosition = 40f

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC)
        paint.textSize = 22f
        canvas.drawText("Quote Service", pageWidth / 2f, yPosition, paint)
        paint.color = Color.parseColor("#8AB6B6")
        canvas.drawLine(50f, yPosition + 5f, pageWidth - 50f, yPosition + 5f, paint)

        yPosition += 40f
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Quote Date", pageWidth / 2f, yPosition, paint)

        yPosition += 15f
        val formatter = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.ENGLISH)
        formatter.timeZone = TimeZone.getTimeZone("America/Mexico_City")
        val formattedDate = formatter.format(date)

        paint.typeface = Typeface.DEFAULT
        canvas.drawText(formattedDate, pageWidth / 2f, yPosition, paint)

        yPosition += 25f

        val billToX = (pageWidth / 2f) - 15f  // Centramos el bloque en el medio aprox
        val boxHeight = 20f
        paint.color = Color.parseColor("#8AB6B6")
        canvas.drawRect(billToX - 10f, yPosition, pageWidth - 20f, yPosition + boxHeight, paint)

        yPosition += 15f
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("BILL TO:", billToX, yPosition, paint)

        yPosition += 15f
        canvas.drawText(customerName, billToX, yPosition, paint)

        yPosition += 15f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(customerAddress, billToX, yPosition, paint)
        yPosition += 15f
        canvas.drawText(customerPhone, billToX, yPosition, paint)
        yPosition += 15f
        canvas.drawText(customerEmail, billToX, yPosition, paint)

        yPosition += 30f

        paint.color = Color.parseColor("#8AB6B6")
        canvas.drawRect(20f, yPosition, pageWidth - 20f, yPosition + 20f, paint)

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Description", 25f, yPosition + 15f, paint)
        canvas.drawText("Qty", 150f, yPosition + 15f, paint)
        canvas.drawText("Unit", 200f, yPosition + 15f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Amount", pageWidth - 25f, yPosition + 15f, paint)
        yPosition += 35f

        products.forEachIndexed { index, (desc, price, quantity) ->
            if (index % 2 == 1) {
                paint.color = Color.argb(30, 0, 0, 0)
                canvas.drawRect(20f, yPosition - 10f, pageWidth - 20f, yPosition + 10f, paint)
            }
            paint.color = Color.BLACK
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(desc, 25f, yPosition, paint)
            canvas.drawText(quantity.toString(), 150f, yPosition, paint)
            canvas.drawText("$%.2f".format(price), 200f, yPosition, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$%.2f".format(price * quantity), pageWidth - 25f, yPosition, paint)
            yPosition += 20f
        }

        listOf(
            "Total Payment" to (total - extra),
            "Extra Charges" to extra,
            "Total" to total
        ).forEachIndexed { i, (label, amount) ->
            if ((products.size + i) % 2 == 1) {
                paint.color = Color.argb(30, 0, 0, 0)
                canvas.drawRect(20f, yPosition - 10f, pageWidth - 20f, yPosition + 10f, paint)
            }
            paint.color = Color.BLACK
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(label, 25f, yPosition, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$%.2f".format(amount), pageWidth - 25f, yPosition, paint)
            yPosition += 20f
        }

        yPosition += 40f

        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 10f
        canvas.drawText("Notes:", 25f, yPosition, paint)
        yPosition += 15f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(notes, 25f, yPosition, paint)

        yPosition += 60f

        pdfDocument.finishPage(page)

        val safeName = customerName.replace("[^a-zA-Z0-9]".toRegex(), "").take(15)
        val fileName = "${safeName}_${SimpleDateFormat("ddMMyyyy_HHmmss", Locale("es", "MX")).format(Date())}.pdf"
        val filePath = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        pdfDocument.writeTo(FileOutputStream(filePath))
        pdfDocument.close()

        Toast.makeText(this, "PDF saved: ${filePath.absolutePath}", Toast.LENGTH_LONG).show()
        openPDF(filePath)
    }

    private fun openPDF(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
            finish()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No PDF viewer installed", Toast.LENGTH_SHORT).show()
        }
    }
}
