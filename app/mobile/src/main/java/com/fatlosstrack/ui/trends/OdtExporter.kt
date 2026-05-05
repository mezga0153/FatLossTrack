package com.fatlosstrack.ui.trends

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates an ODT (OpenDocument Text) file containing a table with [headers] and [rows],
 * saves it in the app's cache dir, and shares it via the system share sheet.
 */
object OdtExporter {

    fun share(
        context: Context,
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
    ) {
        val file = buildOdt(context, title, headers, rows)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.oasis.opendocument.text"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun buildOdt(
        context: Context,
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
    ): File {
        val dir = File(context.cacheDir, "shared_exports").also { it.mkdirs() }
        val safeName = title.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "$safeName.odt")

        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            // ODT spec: mimetype entry MUST be first and MUST be stored (no compression)
            zip.putNextEntry(ZipEntry("mimetype").also { it.method = ZipEntry.STORED; it.size = MIMETYPE.size.toLong(); it.compressedSize = MIMETYPE.size.toLong(); it.crc = crc32(MIMETYPE) })
            zip.write(MIMETYPE)
            zip.closeEntry()

            // META-INF/manifest.xml
            val manifest = buildManifest()
            zip.putNextEntry(ZipEntry("META-INF/manifest.xml"))
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // content.xml
            val content = buildContent(title, headers, rows)
            zip.putNextEntry(ZipEntry("content.xml"))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        return file
    }

    private fun buildManifest(): String = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"
    manifest:version="1.2">
  <manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text"/>
  <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
</manifest:manifest>"""

    private fun buildContent(title: String, headers: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<office:document-content
    xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    office:version="1.2">
  <office:automatic-styles>
    <style:style style:name="HeaderCell" style:family="table-cell">
      <style:table-cell-properties fo:background-color="#333344"/>
    </style:style>
    <style:style style:name="BoldP" style:family="paragraph">
      <style:text-properties fo:font-weight="bold" fo:color="#FFFFFF"/>
    </style:style>
  </office:automatic-styles>
  <office:body>
    <office:text>
      <text:h text:outline-level="1">${title.xmlEscape()}</text:h>
      <table:table table:name="Data">
""")

        // Header columns
        headers.forEach { _ ->
            sb.append("        <table:table-column/>\n")
        }

        // Header row
        sb.append("        <table:table-row>\n")
        headers.forEach { h ->
            sb.append("          <table:table-cell table:style-name=\"HeaderCell\"><text:p text:style-name=\"BoldP\">${h.xmlEscape()}</text:p></table:table-cell>\n")
        }
        sb.append("        </table:table-row>\n")

        // Data rows
        rows.forEach { row ->
            sb.append("        <table:table-row>\n")
            row.forEach { cell ->
                sb.append("          <table:table-cell><text:p>${cell.xmlEscape()}</text:p></table:table-cell>\n")
            }
            sb.append("        </table:table-row>\n")
        }

        sb.append("""      </table:table>
    </office:text>
  </office:body>
</office:document-content>""")
        return sb.toString()
    }

    private fun String.xmlEscape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val MIMETYPE = "application/vnd.oasis.opendocument.text".toByteArray(Charsets.UTF_8)

    private fun crc32(data: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(data)
        return crc.value
    }
}
