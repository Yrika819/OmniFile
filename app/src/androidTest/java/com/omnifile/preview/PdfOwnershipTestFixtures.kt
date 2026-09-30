package com.omnifile.preview

import java.io.ByteArrayOutputStream

internal fun ownershipTestPdf(pageCount: Int, pageWidth: Int = 120, pageHeight: Int = 160): ByteArray {
        val bodies = mutableListOf<String>()
        val kids = (0 until pageCount).joinToString(" ") { index -> "${3 + index * 2} 0 R" }
        bodies += "<< /Type /Catalog /Pages 2 0 R >>"
        bodies += "<< /Type /Pages /Kids [$kids] /Count $pageCount >>"
        repeat(pageCount) { index ->
            val pageObject = 3 + index * 2
            val streamObject = pageObject + 1
            val content = "q 0.15 0.35 0.75 rg 0 0 $pageWidth $pageHeight re f Q"
            bodies += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageWidth $pageHeight] /Resources << >> /Contents $streamObject 0 R >>"
            bodies += "<< /Length ${content.toByteArray().size} >>\nstream\n$content\nendstream"
        }
        val output = ByteArrayOutputStream()
        output.write("%PDF-1.4\n".toByteArray())
        val offsets = mutableListOf(0)
        bodies.forEachIndexed { index, body ->
            offsets += output.size()
            output.write("${index + 1} 0 obj\n$body\nendobj\n".toByteArray())
        }
        val xref = output.size()
        output.write("xref\n0 ${bodies.size + 1}\n0000000000 65535 f \n".toByteArray())
        offsets.drop(1).forEach { offset ->
            output.write("%010d 00000 n \n".format(offset).toByteArray())
        }
        output.write("trailer\n<< /Size ${bodies.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n".toByteArray())
        return output.toByteArray()
    }

