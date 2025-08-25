package com.example.workadministration.ui.quote

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.workadministration.R
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

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
                                .orderBy("position")
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

    @Suppress("DEPRECATION")
    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        } else {
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        }
    }

    private fun generatePDF(
        customerName: String,
        customerAddress: String,
        customerPhone: String,
        customerEmail: String,
        date: Date,
        total: Double,
        notes: String,
        extra: Double,
        products: List<Triple<String, Double, Int>>
    ) {
        val pageWidth = 400
        val notesWidth = 300
        val notesText = notes.ifEmpty { "No notes." }

        val notesTextPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.DEFAULT
        }

        val staticLayoutNotes = createStaticLayout(notesText, notesTextPaint, notesWidth)
        val notesHeight = staticLayoutNotes.height

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, 1200, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // -------- Watermark --------
        val logo = BitmapFactory.decodeResource(resources, R.drawable.logo1)
        val scaledWatermark = Bitmap.createScaledBitmap(logo, 300, 300, false)
        val watermarkPaint = Paint().apply { alpha = 30 }
        val watermarkX = (pageWidth - scaledWatermark.width) / 2f
        val watermarkY = (pageInfo.pageHeight - scaledWatermark.height) / 2f
        canvas.drawBitmap(scaledWatermark, watermarkX, watermarkY, watermarkPaint)

        var yPosition = 40f

        // -------- Título --------
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC)
        paint.textSize = 22f
        paint.color = Color.BLACK
        canvas.drawText("Quote Service", pageWidth / 2f, yPosition, paint)

        paint.color = Color.parseColor("#8AB6B6")
        canvas.drawLine(50f, yPosition + 5f, pageWidth - 50f, yPosition + 5f, paint)

        yPosition += 40f

        // -------- Fecha --------
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

        // -------- Datos Cliente --------
        val billToX = (pageWidth / 2f) - 15f
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
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(customerName, billToX, yPosition, paint)
        yPosition += 15f
        canvas.drawText(customerAddress, billToX, yPosition, paint)
        yPosition += 15f
        canvas.drawText(customerPhone, billToX, yPosition, paint)
        yPosition += 15f
        canvas.drawText(customerEmail, billToX, yPosition, paint)

        yPosition += 30f

        // -------- Encabezado tabla --------
        val headerHeight = 25f
        paint.color = Color.parseColor("#8AB6B6")
        canvas.drawRect(20f, yPosition, pageWidth - 20f, yPosition + headerHeight, paint)

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT_BOLD

        val headerCenterY = (yPosition + yPosition + headerHeight) / 2f - (paint.descent() + paint.ascent()) / 2f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Description", 25f, headerCenterY, paint)
        canvas.drawText("Qty", 150f, headerCenterY, paint)
        canvas.drawText("Unit", 200f, headerCenterY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Amount", pageWidth - 25f, headerCenterY, paint)

        yPosition += headerHeight + 10f

        // -------- Productos --------
        val descMaxWidth = 110f
        val rowBaseHeight = 20f

        products.forEachIndexed { index, (desc, price, quantity) ->
            val textPaint = TextPaint().apply {
                isAntiAlias = true
                color = Color.BLACK
                textSize = 10f
                typeface = Typeface.DEFAULT
            }
            val staticLayoutDesc = createStaticLayout(desc, textPaint, descMaxWidth.toInt())
            val rowHeight = max(rowBaseHeight.toInt(), staticLayoutDesc.height + 10)

            if (index % 2 == 1) {
                paint.color = Color.argb(30, 0, 0, 0)
                canvas.drawRect(20f, yPosition, pageWidth - 20f, yPosition + rowHeight, paint)
            }

            // descripción multilínea
            canvas.save()
            canvas.translate(25f, yPosition + 5f)
            staticLayoutDesc.draw(canvas)
            canvas.restore()

            val centerY = (yPosition + yPosition + rowHeight) / 2f - (paint.descent() + paint.ascent()) / 2f
            paint.color = Color.BLACK
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(quantity.toString(), 150f, centerY, paint)
            canvas.drawText("$%.2f".format(price), 200f, centerY, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$%.2f".format(price * quantity), pageWidth - 25f, centerY, paint)

            yPosition += rowHeight
        }

        // -------- Totales --------
        val labels = listOf("Total Payment", "Extra Charges", "Total")
        val maxLabelWidth = labels.maxOf { paint.measureText(it) }
        val totalRowHeight = 25f

        listOf(
            "Total Payment" to (total - extra),
            "Extra Charges" to extra,
            "Total" to total
        ).forEachIndexed { i, (label, amount) ->
            if ((products.size + i) % 2 == 1) {
                paint.color = Color.argb(30, 0, 0, 0)
                canvas.drawRect(20f, yPosition, pageWidth - 20f, yPosition + totalRowHeight, paint)
            }

            paint.color = Color.BLACK
            paint.typeface = Typeface.DEFAULT_BOLD

            val centerY = (yPosition + yPosition + totalRowHeight) / 2f - (paint.descent() + paint.ascent()) / 2f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(label, 25f + maxLabelWidth, centerY, paint)
            canvas.drawText("$%.2f".format(amount), pageWidth - 25f, centerY, paint)

            yPosition += totalRowHeight
        }

        yPosition += 30f

        // -------- Notas --------
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 12f
        paint.color = Color.BLACK
        canvas.drawText("Notes:", 25f, yPosition, paint)
        yPosition += 15f

        canvas.save()
        canvas.translate(25f, yPosition)
        staticLayoutNotes.draw(canvas)
        canvas.restore()

        yPosition += notesHeight + 20f

        // -------- Fechas emisión y vencimiento --------
        val issueFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
        issueFormatter.timeZone = TimeZone.getTimeZone("America/Mexico_City")
        val issueDate = issueFormatter.format(date)

        val calendar = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, 30)
        }
        val expiryDate = issueFormatter.format(calendar.time)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 11f
        paint.color = Color.BLACK
        canvas.drawText("Issued on: $issueDate", pageWidth / 2f, yPosition, paint)
        yPosition += 15f
        canvas.drawText("Valid until: $expiryDate", pageWidth / 2f, yPosition, paint)

        yPosition += 25f

        // -------- Aviso validez --------
        val validityNotice =
            "This quotation shall remain valid for thirty (30) days from the date of issue. After this period, a new quotation must be requested."
        val noticePaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.DEFAULT
        }
        val staticLayoutNotice = createStaticLayout(validityNotice, noticePaint, pageWidth - 40)
        canvas.save()
        canvas.translate(20f, yPosition)
        staticLayoutNotice.draw(canvas)
        canvas.restore()

        yPosition += staticLayoutNotice.height + 20f

        pdfDocument.finishPage(page)

        // -------- Guardar --------
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
