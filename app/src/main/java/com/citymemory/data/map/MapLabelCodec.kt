package com.citymemory.data.map

import com.citymemory.domain.model.LabelTier
import com.citymemory.domain.model.MapLabel
import java.io.IOException
import java.io.InputStream

/**
 * Reads the label list produced by `tools/build_labels.py`.
 *
 * Tab-separated with a magic header, the same shape as the place catalog and
 * for the same reasons — 219 rows of five short fields, read once, and
 * `split('\t')` needs no parser. Like every other codec here it takes an
 * [InputStream] rather than an `AssetManager`, so the file that ships in the
 * APK is the file a plain JVM test asserts against.
 *
 * ```
 * CMLB <tab> version <tab> count <tab> stamp
 * tier <tab> name <tab> lat <tab> lon <tab> detail
 * <one row per label, same columns>
 * ```
 *
 * The writer collapses whitespace in every field, so there is no escape
 * character and none is needed.
 */
object MapLabelCodec {

    private const val MAGIC = "CMLB"
    private const val VERSION = 1
    private const val COLUMNS = 5

    class MalformedLabelsException(message: String) : IOException(message)

    fun decode(input: InputStream): List<MapLabel> {
        val lines = input.bufferedReader().readLines()
        if (lines.size < 2) throw MalformedLabelsException("label file is empty")

        val header = lines[0].split('\t')
        if (header.getOrNull(0) != MAGIC) {
            throw MalformedLabelsException("not a label file: '${header.getOrNull(0)}'")
        }
        val version = header.getOrNull(1)?.toIntOrNull()
        if (version != VERSION) throw MalformedLabelsException("unsupported version $version")
        val declared = header.getOrNull(2)?.toIntOrNull()
            ?: throw MalformedLabelsException("header carries no row count")

        val labels = ArrayList<MapLabel>(declared)
        for (index in 2 until lines.size) {
            val line = lines[index]
            if (line.isBlank()) continue
            val f = line.split('\t')
            if (f.size != COLUMNS) {
                throw MalformedLabelsException(
                    "row ${index + 1} has ${f.size} fields, expected $COLUMNS",
                )
            }
            val tier = f[0].toIntOrNull()?.let { LabelTier.fromId(it) }
                ?: throw MalformedLabelsException("row ${index + 1} has no known tier: '${f[0]}'")
            val latitude = f[2].toDoubleOrNull()
            val longitude = f[3].toDoubleOrNull()
            if (latitude == null || longitude == null) {
                throw MalformedLabelsException("row ${index + 1} has no usable coordinate")
            }
            labels += MapLabel(
                tier = tier,
                name = f[1],
                latitude = latitude,
                longitude = longitude,
                detail = f[4],
            )
        }

        if (labels.size != declared) {
            throw MalformedLabelsException("header says $declared labels, found ${labels.size}")
        }
        return labels
    }
}
