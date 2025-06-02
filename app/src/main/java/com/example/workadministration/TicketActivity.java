package com.example.workadministration;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class TicketActivity {
    public void generarPDFTicket(Context context, model.Ticket ticket) {
        PdfDocument pdfDocument = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(300, 600, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        paint.setTextSize(12);
        paint.setColor(Color.BLACK);

        int y = 20;

        canvas.drawText("TICKET", 100, y, paint);
        y += 20;
        canvas.drawText("Cliente: " + ticket.cliente, 10, y, paint);
        y += 20;

        canvas.drawText("Productos:", 10, y, paint);
        y += 20;

        for (model.Producto producto : ticket.productos) {
            String linea = "- " + producto.nombre + " x" + producto.cantidad + " - $" + producto.precio;
            canvas.drawText(linea, 10, y, paint);
            y += 20;
        }

        y += 10;
        canvas.drawText("Total: $" + ticket.total, 10, y, paint);
        y += 20;
        canvas.drawText("Notas: " + ticket.notas, 10, y, paint);

        pdfDocument.finishPage(page);

        // Guardar el PDF
        File file = new File(context.getExternalFilesDir(null), "Ticket_" + System.currentTimeMillis() + ".pdf");
        try {
            pdfDocument.writeTo(new FileOutputStream(file));
            Toast.makeText(context, "PDF guardado en:\n" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error al guardar PDF", Toast.LENGTH_SHORT).show();
        }

        pdfDocument.close();
    }

}
