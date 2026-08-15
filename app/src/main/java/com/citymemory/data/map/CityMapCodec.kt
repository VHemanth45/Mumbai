package com.citymemory.data.map

import com.citymemory.domain.model.CityGeometry
import com.citymemory.domain.model.CityShape
import com.citymemory.domain.model.GeoBounds
import com.citymemory.domain.model.ShapeKind
import java.io.IOException
import java.io.InputStream

/**
 * Reads the packed vector map produced by `tools/build_map_asset.py`.
 *
 * The format is deliberately tiny and dumb — no compression beyond delta +
 * varint coding, no indexes, no strings — because the whole point is that it
 * decodes in one linear pass with no allocation per point. Mumbai is ~867,000
 * points in 3.6 MB, which lands as flat `DoubleArray`s rather than 867,000
 * boxed objects.
 *
 * Layout (little-endian where fixed width, LEB128 where varint):
 *
 * ```
 * "CMAP"                                    4 bytes
 * version   u8 = 1
 * bounds    4 x i32   minLat, minLng, maxLat, maxLng   (degrees * 1e6)
 * shapes    varint count
 *   kind      u8                            ShapeKind.id
 *   points    varint count
 *   lat0,lng0 zigzag varint, absolute       (degrees * 1e6)
 *   ...       (count-1) zigzag varint delta pairs
 * ```
 *
 * Pure Kotlin on purpose: it takes an [InputStream], not an `AssetManager`, so
 * the real asset can be decoded and asserted against in an ordinary JVM test.
 */
object CityMapCodec {

    private const val MAGIC = "CMAP"
    private const val VERSION = 1
    private const val PRECISION = 1e6

    class MalformedMapException(message: String) : IOException(message)

    fun decode(cityId: String, input: InputStream): CityGeometry {
        val bytes = input.readBytes()
        val reader = Reader(bytes)

        val magic = String(bytes, 0, 4, Charsets.US_ASCII)
        if (magic != MAGIC) throw MalformedMapException("not a city map: '$magic'")
        reader.pos = 4

        val version = reader.u8()
        if (version != VERSION) throw MalformedMapException("unsupported version $version")

        val bounds = GeoBounds(
            minLatitude = reader.i32() / PRECISION,
            minLongitude = reader.i32() / PRECISION,
            maxLatitude = reader.i32() / PRECISION,
            maxLongitude = reader.i32() / PRECISION,
        )

        val shapeCount = reader.varint()
        val shapes = ArrayList<CityShape>(shapeCount)

        repeat(shapeCount) {
            val kindId = reader.u8()
            val kind = ShapeKind.fromId(kindId)
                ?: throw MalformedMapException("unknown shape kind $kindId")

            val pointCount = reader.varint()
            val latitudes = DoubleArray(pointCount)
            val longitudes = DoubleArray(pointCount)

            var lat = 0L
            var lng = 0L
            for (i in 0 until pointCount) {
                lat += reader.zigzag()
                lng += reader.zigzag()
                latitudes[i] = lat / PRECISION
                longitudes[i] = lng / PRECISION
            }

            // A kind the renderer does not know how to close would draw as
            // garbage; dropping is better than crashing on a bad asset.
            if (kind.isArea && pointCount < 3) return@repeat
            if (!kind.isArea && pointCount < 2) return@repeat

            shapes += CityShape(kind, latitudes, longitudes)
        }

        return CityGeometry(cityId = cityId, bounds = bounds, shapes = shapes)
    }

    /**
     * A cursor over the decoded bytes. Deltas are accumulated as `Long` so a
     * corrupt run of large values cannot silently wrap an `Int`.
     */
    private class Reader(private val bytes: ByteArray) {
        var pos = 0

        private fun next(): Int {
            if (pos >= bytes.size) throw MalformedMapException("truncated map data")
            return bytes[pos++].toInt() and 0xFF
        }

        fun u8(): Int = next()

        fun i32(): Int =
            next() or (next() shl 8) or (next() shl 16) or (next() shl 24)

        fun varint(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val b = next()
                result = result or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift > 35) throw MalformedMapException("varint overflow")
            }
        }

        fun zigzag(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = next()
                result = result or ((b.toLong() and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                if (shift > 63) throw MalformedMapException("varint overflow")
            }
            return (result ushr 1) xor -(result and 1L)
        }
    }
}
