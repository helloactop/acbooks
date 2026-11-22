package app.acbooks.reader

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

internal fun <T> readPdf(file: File, read: (PdfRenderer) -> T): T {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = try {
        PdfRenderer(descriptor)
    } catch (error: Exception) {
        descriptor.close()
        throw error
    }
    return renderer.use(read)
}
