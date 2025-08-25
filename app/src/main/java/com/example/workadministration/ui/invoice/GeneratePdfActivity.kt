package com.example.workadministration.ui.invoice

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
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

class GeneratePdfActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var invoiceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        invoiceId = intent.getStringExtra("invoiceId")

        if (invoiceId.isNullOrEmpty()) {
            Toast.makeText(this, "Invoice ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fetchInvoiceData()
    }

    private fun fetchInvoiceData() {
        db.collection("invoices").document(invoiceId!!)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val customerId = doc.getString("customerId") ?: ""
                    val total = doc.getDouble("total") ?: 0.0
                    val notes = doc.getString("notes") ?: ""
                    val extra = doc.getDouble("extraCharges") ?: 0.0

                    // Manejo seguro de la fecha
                    val dateField = doc.get("date")
                    val date = when (dateField) {
                        is com.google.firebase.Timestamp -> dateField.toDate()
                        is String -> {
                            try {
                                val formatter = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale("en", "US"))
                                formatter.timeZone = TimeZone.getTimeZone("America/Mexico_City")
                                formatter.parse(dateField) ?: Date()
                            } catch (e: Exception) {
                                Date()
                            }
                        }
                        else -> Date()
                    }

                    db.collection("customers").document(customerId)
                        .get()
                        .addOnSuccessListener { customerDoc ->
                            val customerName = customerDoc.getString("fullname") ?: ""
                            val customerAddress = customerDoc.getString("address") ?: ""
                            val customerPhone = customerDoc.getString("phone") ?: ""
                            val customerEmail = customerDoc.getString("email") ?: ""

                            db.collection("invoices").document(invoiceId!!)
                                .collection("invoiceDetails")
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
                    Toast.makeText(this, "Invoice not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching invoice", Toast.LENGTH_SHORT).show()
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

        // --- Utilidades ---
        fun cmToPt(cm: Float): Float = cm * 28.3465f // 1 cm ≈ 28.3465 pt (PostScript points)

        // --- Parámetros de layout ---
        val pageWidth = 400
        val leftMargin = 25f
        val rightMargin = 25f
        val tableSidePadding = 20f
        val zebraAlpha = 30
        val headerHeight = 25f
        val headerGap = 10f
        val minRowHeight = 20f
        val rowVerticalPadding = 4f
        val gapCm = 1.5f
        val gapPx = cmToPt(gapCm)

        // Columnas (flexibles a partir del ancho de página)
        val amountRightX = pageWidth - rightMargin
        val unitX = amountRightX - 90f       // deja espacio para amounts (≈90pt)
        val qtyX = unitX - 60f               // ancho típico para qty
        val descLeftX = leftMargin
        val descMaxWidth = (qtyX - descLeftX - gapPx).coerceAtLeast(80f)

        // --- Pinturas de texto ---
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.DEFAULT
        }
        val boldPaint = Paint(bodyPaint).apply {
            typeface = Typeface.DEFAULT_BOLD
        }
        val notesTextPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.DEFAULT
        }

        val notesText = notes.ifEmpty { "No notes." }
        val staticLayoutNotes = createStaticLayout(notesText, notesTextPaint, 300)
        val notesHeight = staticLayoutNotes.height

        // --- PASADA DE MEDICIÓN (para calcular pageHeight dinámico) ---
        var yMeasure = 40f
        // Encabezado
        yMeasure += 40f          // después del título y línea
        yMeasure += 15f          // "Invoice Date"
        yMeasure += 25f          // fecha
        // Bill To block
        val billBoxHeight = 20f
        // Se dibuja, pero la y real la manejas con saltos siguientes (texto)
        yMeasure += 15f // BILL TO:
        yMeasure += 15f // name
        yMeasure += 15f // address
        yMeasure += 15f // phone
        yMeasure += 15f // email
        yMeasure += 30f

        // Cabecera de tabla
        yMeasure += headerHeight + headerGap

        // Productos (altura dinámica por descripción)
        val measureTextPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.DEFAULT
        }
        val rowHeights = ArrayList<Float>(products.size)

        products.forEach { (desc, _, _) ->
            val layout = createStaticLayout(desc, measureTextPaint, descMaxWidth.toInt())
            val descHeight = layout.height.toFloat()
            var rowHeight = descHeight + 2 * rowVerticalPadding
            if (rowHeight < minRowHeight) rowHeight = minRowHeight
            rowHeights += rowHeight
            yMeasure += rowHeight
        }

        // Totales (3 filas fijas)
        val totalRowHeight = 30f
        yMeasure += totalRowHeight * 3

        // Notas
        yMeasure += 15f          // "Notes:"
        yMeasure += notesHeight + 20f

        // Altura final de la página (un mínimo de seguridad)
        val baseMinHeight = 750f
        val pageHeight = maxOf(baseMinHeight, yMeasure + 40f).toInt()

        // --- Construcción del PDF ---
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Watermark
        val logo = BitmapFactory.decodeResource(resources, R.drawable.logo1)
        val scaledWatermark = Bitmap.createScaledBitmap(logo, 300, 300, false)
        val watermarkPaint = Paint().apply { alpha = 30 }
        val watermarkX = (pageWidth - scaledWatermark.width) / 2f
        val watermarkY = (pageHeight - scaledWatermark.height) / 2f
        canvas.drawBitmap(scaledWatermark, watermarkX, watermarkY, watermarkPaint)

        var yPosition = 40f

        // ----------- ENCABEZADO -----------
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC)
        paint.textSize = 22f
        paint.color = Color.BLACK
        canvas.drawText("Invoice Service", pageWidth / 2f, yPosition, paint)
        paint.color = Color.parseColor("#8AB6B6")
        canvas.drawLine(50f, yPosition + 5f, pageWidth - 50f, yPosition + 5f, paint)

        yPosition += 40f
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Invoice Date", pageWidth / 2f, yPosition, paint)

        yPosition += 15f
        val formatter = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("America/Mexico_City")
        }
        val formattedDate = formatter.format(date)

        paint.typeface = Typeface.DEFAULT
        canvas.drawText(formattedDate, pageWidth / 2f, yPosition, paint)

        yPosition += 25f

        // BILL TO box
        paint.color = Color.parseColor("#8AB6B6")
        canvas.drawRect((pageWidth / 2f) - 25f, yPosition, pageWidth - 20f, yPosition + billBoxHeight, paint)

        yPosition += 15f
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT_BOLD
        val billToX = (pageWidth / 2f) - 15f
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

        // ----------- CABECERA DE TABLA -----------
        paint.color = Color.parseColor("#8AB6B6")
        canvas.drawRect(tableSidePadding, yPosition, pageWidth - tableSidePadding, yPosition + headerHeight, paint)

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT_BOLD

        val headerCenterY = (yPosition + yPosition + headerHeight) / 2f - (bodyPaint.descent() + bodyPaint.ascent()) / 2f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Description", descLeftX, headerCenterY, paint)
        canvas.drawText("Qty", qtyX, headerCenterY, paint)
        canvas.drawText("Unit", unitX, headerCenterY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Amount", amountRightX, headerCenterY, paint)

        yPosition += headerHeight + headerGap

        fun measureRowHeight(desc: String, paint: TextPaint, maxWidth: Int): Float {
            val layout = createStaticLayout(desc, paint, maxWidth)
            val descHeight = layout.height.toFloat()
            var rowHeight = descHeight + 2 * rowVerticalPadding
            if (rowHeight < minRowHeight) rowHeight = minRowHeight
            return rowHeight
        }

        // ----------- PRODUCTOS (filas de altura dinámica) -----------
        products.forEachIndexed { index, (desc, price, quantity) ->
            val rowHeight = rowHeights[index]   // usar medida previa
            val staticLayoutDesc = createStaticLayout(desc, TextPaint(bodyPaint), descMaxWidth.toInt())

            // Zebra
            if (index % 2 == 1) {
                paint.color = Color.argb(zebraAlpha, 0, 0, 0)
                canvas.drawRect(tableSidePadding, yPosition, pageWidth - tableSidePadding, yPosition + rowHeight, paint)
            }

            // Dibujar descripción
            canvas.save()
            val descOffsetY = yPosition + (rowHeight - staticLayoutDesc.height) / 2f
            canvas.translate(descLeftX, descOffsetY)
            staticLayoutDesc.draw(canvas)
            canvas.restore()

            // Columnas numéricas centradas verticalmente
            paint.color = Color.BLACK
            paint.typeface = Typeface.DEFAULT
            val centerY = (yPosition + yPosition + rowHeight) / 2f - (bodyPaint.descent() + bodyPaint.ascent()) / 2f

            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(quantity.toString(), qtyX, centerY, paint)
            canvas.drawText("$%.2f".format(price), unitX, centerY, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$%.2f".format(price * quantity), amountRightX, centerY, paint)

            yPosition += rowHeight
        }

        // ----------- TOTALES -----------
        val labels = listOf("Total Payment", "Extra Charges", "Total")
        val maxLabelWidth = labels.maxOf { boldPaint.measureText(it) }
        val totals = listOf(
            "Total Payment" to (total - extra),
            "Extra Charges" to extra,
            "Total" to total
        )

        totals.forEachIndexed { i, (label, amount) ->
            if ((products.size + i) % 2 == 1) {
                paint.color = Color.argb(zebraAlpha, 0, 0, 0)
                canvas.drawRect(tableSidePadding, yPosition, pageWidth - tableSidePadding, yPosition + totalRowHeight, paint)
            }

            val centerY = (yPosition + yPosition + totalRowHeight) / 2f - (boldPaint.descent() + boldPaint.ascent()) / 2f

            // Label derecha en columna izquierda
            paint.color = Color.BLACK
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(label, descLeftX + maxLabelWidth, centerY, paint)

            // Monto derecha
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$%.2f".format(amount), amountRightX, centerY, paint)

            yPosition += totalRowHeight
        }

        // ----------- NOTAS -----------
        yPosition += 20f
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 12f
        paint.color = Color.BLACK
        canvas.drawText("Notes:", leftMargin, yPosition, paint)
        yPosition += 15f

        canvas.save()
        canvas.translate(leftMargin, yPosition)
        staticLayoutNotes.draw(canvas)
        canvas.restore()

        yPosition += notesHeight + 20f

        pdfDocument.finishPage(page)

        // ----------- NUEVA PÁGINA CON MÉTODOS DE PAGO -----------
        val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        val canvas2 = page2.canvas
        val paint2 = Paint()

        paint2.textAlign = Paint.Align.CENTER
        paint2.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint2.textSize = 18f
        paint2.color = Color.BLACK
        canvas2.drawText("Payment Methods", pageWidth / 2f, 50f, paint2)

        val venmo = BitmapFactory.decodeResource(resources, R.drawable.venmoapp)
        val cashapp = BitmapFactory.decodeResource(resources, R.drawable.cashapp)

        fun scaleBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val newHeight = (maxWidth / ratio).toInt()
            return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, false)
        }

        val qrSize = 200
        val venmoScaled = scaleBitmap(venmo, qrSize)
        val cashappScaled = scaleBitmap(cashapp, qrSize)

        var yPosition2 = 120f
        val venmoX = (pageWidth - venmoScaled.width) / 2f
        canvas2.drawBitmap(venmoScaled, venmoX, yPosition2, paint2)

        paint2.textSize = 14f
        paint2.typeface = Typeface.DEFAULT
        canvas2.drawText("Venmo", pageWidth / 2f, yPosition2 + venmoScaled.height + 20f, paint2)

        yPosition2 += venmoScaled.height + 80f
        val cashappX = (pageWidth - cashappScaled.width) / 2f
        canvas2.drawBitmap(cashappScaled, cashappX, yPosition2, paint2)

        canvas2.drawText("CashApp", pageWidth / 2f, yPosition2 + cashappScaled.height + 20f, paint2)

        pdfDocument.finishPage(page2)

        // ----------- GUARDAR PDF -----------
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
