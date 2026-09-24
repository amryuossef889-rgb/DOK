package com.dok.editor.engine.effects

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader

data class CubeLutData(
    val title: String,
    val size: Int,
    val domainMin: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val domainMax: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
    val table: FloatArray // size * size * size * 3 floats
) {
    /**
     * Performs trilinear interpolation lookup on the 3D LUT.
     * r, g, b in [0.0 .. 1.0]
     */
    fun sample(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val cr = r.coerceIn(domainMin.first, domainMax.first)
        val cg = g.coerceIn(domainMin.second, domainMax.second)
        val cb = b.coerceIn(domainMin.third, domainMax.third)

        val maxIdx = (size - 1).toFloat()
        val fr = cr * maxIdx
        val fg = cg * maxIdx
        val fb = cb * maxIdx

        val r0 = fr.toInt().coerceIn(0, size - 1)
        val r1 = (r0 + 1).coerceIn(0, size - 1)
        val g0 = fg.toInt().coerceIn(0, size - 1)
        val g1 = (g0 + 1).coerceIn(0, size - 1)
        val b0 = fb.toInt().coerceIn(0, size - 1)
        val b1 = (b0 + 1).coerceIn(0, size - 1)

        val dr = fr - r0
        val dg = fg - g0
        val db = fb - b0

        fun getRgb(ri: Int, gi: Int, bi: Int): Triple<Float, Float, Float> {
            val idx = (bi * size * size + gi * size + ri) * 3
            return Triple(table[idx], table[idx + 1], table[idx + 2])
        }

        val c000 = getRgb(r0, g0, b0)
        val c100 = getRgb(r1, g0, b0)
        val c010 = getRgb(r0, g1, b0)
        val c110 = getRgb(r1, g1, b0)
        val c001 = getRgb(r0, g0, b1)
        val c101 = getRgb(r1, g0, b1)
        val c011 = getRgb(r0, g1, b1)
        val c111 = getRgb(r1, g1, b1)

        // Interpolate along R
        val c00 = lerp(c000, c100, dr)
        val c10 = lerp(c010, c110, dr)
        val c01 = lerp(c001, c101, dr)
        val c11 = lerp(c011, c111, dr)

        // Interpolate along G
        val c0 = lerp(c00, c10, dg)
        val c1 = lerp(c01, c11, dg)

        // Interpolate along B
        return lerp(c0, c1, db)
    }

    private fun lerp(
        a: Triple<Float, Float, Float>,
        b: Triple<Float, Float, Float>,
        t: Float
    ): Triple<Float, Float, Float> {
        return Triple(
            a.first + (b.first - a.first) * t,
            a.second + (b.second - a.second) * t,
            a.third + (b.third - a.third) * t
        )
    }
}

object CubeLutParser {

    fun parse(cubeContent: String): CubeLutData {
        return parse(BufferedReader(StringReader(cubeContent)))
    }

    fun parse(inputStream: InputStream): CubeLutData {
        return parse(BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)))
    }

    private fun parse(reader: BufferedReader): CubeLutData {
        var title = "Untitled LUT"
        var size = 0
        var domainMin = Triple(0f, 0f, 0f)
        var domainMax = Triple(1f, 1f, 1f)
        var table: FloatArray? = null
        var tableIndex = 0

        reader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue

                val parts = line.split("\\s+".toRegex())
                when {
                    parts[0].equals("TITLE", ignoreCase = true) -> {
                        title = line.substringAfter("TITLE").trim().removeSurrounding("\"")
                    }
                    parts[0].equals("LUT_3D_SIZE", ignoreCase = true) -> {
                        size = parts[1].toInt()
                        table = FloatArray(size * size * size * 3)
                    }
                    parts[0].equals("DOMAIN_MIN", ignoreCase = true) && parts.size >= 4 -> {
                        domainMin = Triple(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat())
                    }
                    parts[0].equals("DOMAIN_MAX", ignoreCase = true) && parts.size >= 4 -> {
                        domainMax = Triple(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat())
                    }
                    parts.size >= 3 && table != null -> {
                        val r = parts[0].toFloatOrNull()
                        val g = parts[1].toFloatOrNull()
                        val b = parts[2].toFloatOrNull()
                        if (r != null && g != null && b != null && tableIndex + 2 < table!!.size) {
                            table!![tableIndex++] = r
                            table!![tableIndex++] = g
                            table!![tableIndex++] = b
                        }
                    }
                }
            }
        }

        require(size > 0) { "Invalid or missing LUT_3D_SIZE in .cube file" }
        require(table != null && tableIndex == table!!.size) {
            "Mismatch in LUT entries: expected ${table?.size} values but parsed $tableIndex"
        }

        return CubeLutData(
            title = title,
            size = size,
            domainMin = domainMin,
            domainMax = domainMax,
            table = table!!
        )
    }
}
