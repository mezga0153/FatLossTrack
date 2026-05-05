package com.fatlosstrack.ui.trends

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates an ODS (OpenDocument Spreadsheet) file containing a sheet with [headers] and [rows],
 * saves it in the app's cache dir, and shares it via the system share sheet.
 */
object OdtExporter {

    fun share(
        context: Context,
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
    ) {
        val file = buildOds(context, title, headers, rows)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.oasis.opendocument.spreadsheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun buildOds(
        context: Context,
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
    ): File {
        val dir = File(context.cacheDir, "shared_exports").also { it.mkdirs() }
        val safeName = title.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "$safeName.ods")

        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            // ODS spec: mimetype MUST be first, STORED (no compression)
            zip.putNextEntry(ZipEntry("mimetype").also {
                it.method = ZipEntry.STORED
                it.size = MIMETYPE.size.toLong()
                it.compressedSize = MIMETYPE.size.toLong()
                it.crc = crc32(MIMETYPE)
            })
            zip.write(MIMETYPE)
            zip.closeEntry()

            // META-INF/manifest.xml
            zip.putNextEntry(ZipEntry("META-INF/manifest.xml"))
            zip.write(buildManifest().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // content.xml
            zip.putNextEntry(ZipEntry("content.xml"))
            zip.write(buildContent(title, headers, rows).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        return file
    }

    private fun buildManifest() = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"
    manifest:version="1.2">
  <manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.spreadsheet"/>
  <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
</manifest:manifest>"""

    private fun buildContent(title: String, headers: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<office:document-content
    xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    xmlns:number="urn:oasis:names:tc:opendocument:xmlns:datastyle:1.0"
    xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    office:version="1.2">
  <office:automatic-styles>
    <style:style style:name="Header" style:family="table-cell">
      <style:table-cell-properties fo:background-color="#1F1F3A"/>
      <style:text-properties fo:font-weight="bold" fo:color="#6C9CFF"/>
    </style:style>
    <style:style style:name="NumCell" style:family="table-cell">
      <style:table-cell-properties/>
    </style:style>
  </office:automatic-styles>
  <office:body>
    <office:spreadsheet>
      <table:table table:name="${title.xmlEscape()}">
""")

        // Header row
        sb.append("        <table:table-row>\n")
        headers.forEach { h ->
            sb.append("          <table:table-cell table:style-name=\"Header\" office:value-type=\"string\"><text:p>${h.xmlEscape()}</text:p></table:table-cell>\n")
        }
        sb.append("        </table:table-row>\n")

        // Data rows — detect numeric cells to use office:value-type="float" for proper spreadsheet behaviour
        rows.forEach { row ->
            sb.append("        <table:table-row>\n")
            row.forEach { cell ->
                val num = cell.toDoubleOrNull()
                if (num != null) {
                    sb.append("          <table:table-cell office:value-type=\"float\" office:value=\"$cell\"><text:p>$cell</text:p></table:table-cell>\n")
                } else {
                    sb.append("          <table:table-cell office:value-type=\"string\"><text:p>${cell.xmlEscape()}</text:p></table:table-cell>\n")
                }
            }
            sb.append("        </table:table-row>\n")
        }

        sb.append("""      </table:table>
    </office:spreadsheet>
  </office:body>
</office:document-content>""")
        return sb.toString()
    }

    private fun String.xmlEscape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val MIMETYPE = "application/vnd.oasis.opendocument.spreadsheet".toByteArray(Charsets.UTF_8)

    private fun crc32(data: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(data)
        return crc.value
    }
}
