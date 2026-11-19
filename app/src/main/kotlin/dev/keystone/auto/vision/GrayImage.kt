package dev.keystone.auto.vision

/** An 8-bit grayscale image. Pure Kotlin so the vision code runs in plain JVM unit tests. */
class GrayImage(val w: Int, val h: Int, val data: ByteArray) {
    init {
        require(data.size == w * h) { "data size ${data.size} != $w x $h" }
    }

    operator fun get(x: Int, y: Int): Int = data[y * w + x].toInt() and 0xFF

    /** Box-average downscale by an integer factor. */
    fun downscale(f: Int): GrayImage {
        if (f <= 1) return this
        val nw = w / f
        val nh = h / f
        val out = ByteArray(nw * nh)
        val area = f * f
        for (y in 0 until nh) for (x in 0 until nw) {
            var s = 0
            for (dy in 0 until f) {
                val row = (y * f + dy) * w + x * f
                for (dx in 0 until f) s += data[row + dx].toInt() and 0xFF
            }
            out[y * nw + x] = (s / area).toByte()
        }
        return GrayImage(nw, nh, out)
    }

    /** Box blur with the given radius (separable, via running sums). */
    fun boxBlur(radius: Int): GrayImage {
        if (radius <= 0) return this
        val tmp = IntArray(w * h)
        val out = ByteArray(w * h)
        val win = 2 * radius + 1
        for (y in 0 until h) {                              // horizontal pass
            var s = 0
            val row = y * w
            for (x in -radius..radius) s += this[x.coerceIn(0, w - 1), y]
            for (x in 0 until w) {
                tmp[row + x] = s
                val add = this[(x + radius + 1).coerceIn(0, w - 1), y]
                val sub = this[(x - radius).coerceIn(0, w - 1), y]
                s += add - sub
            }
        }
        for (x in 0 until w) {                              // vertical pass
            var s = 0
            for (y in -radius..radius) s += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = (s / (win * win)).toByte()
                val add = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                val sub = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
                s += add - sub
            }
        }
        return GrayImage(w, h, out)
    }

    fun histogram(): IntArray {
        val hist = IntArray(256)
        for (b in data) hist[b.toInt() and 0xFF]++
        return hist
    }

    /** Value below which `p` (0..1) of the pixels lie. */
    fun percentile(p: Double): Int {
        val hist = histogram()
        val target = (p * data.size).toLong()
        var acc = 0L
        for (v in 0..255) {
            acc += hist[v]
            if (acc >= target) return v
        }
        return 255
    }

    /** Otsu's threshold. */
    fun otsu(): Int {
        val hist = histogram()
        val total = data.size.toDouble()
        var sumAll = 0.0
        for (v in 0..255) sumAll += v * hist[v].toDouble()
        var wB = 0.0
        var sumB = 0.0
        var best = 0.0
        var thr = 0
        for (v in 0..255) {
            wB += hist[v]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += v * hist[v].toDouble()
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between
                thr = v
            }
        }
        return thr
    }

    companion object {
        /** From Android ARGB pixels (Bitmap.getPixels): Rec. 601 luma. */
        fun fromArgb(px: IntArray, w: Int, h: Int): GrayImage {
            val out = ByteArray(w * h)
            for (i in px.indices) {
                val c = px[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                out[i] = ((299 * r + 587 * g + 114 * b) / 1000).toByte()
            }
            return GrayImage(w, h, out)
        }

        /** From a camera Y plane that may have a row stride larger than the width. */
        fun fromLuma(y: java.nio.ByteBuffer, w: Int, h: Int, rowStride: Int, pixelStride: Int = 1): GrayImage {
            val out = ByteArray(w * h)
            for (row in 0 until h) {
                val base = row * rowStride
                for (col in 0 until w) out[row * w + col] = y.get(base + col * pixelStride)
            }
            return GrayImage(w, h, out)
        }
    }
}

/** Binary-mask operations (1 = set), built on integral images so they are O(pixels) for any kernel size. */
object Mask {
    fun threshold(img: GrayImage, thr: Int): ByteArray {
        val m = ByteArray(img.data.size)
        for (i in m.indices) if ((img.data[i].toInt() and 0xFF) > thr) m[i] = 1
        return m
    }

    private fun integral(m: ByteArray, w: Int, h: Int): IntArray {
        val ww = w + 1
        val ii = IntArray(ww * (h + 1))
        for (y in 0 until h) {
            var row = 0
            for (x in 0 until w) {
                row += m[y * w + x]
                ii[(y + 1) * ww + x + 1] = ii[y * ww + x + 1] + row
            }
        }
        return ii
    }

    private fun windowSum(ii: IntArray, ww: Int, x0: Int, y0: Int, x1: Int, y1: Int) =
        ii[y1 * ww + x1] - ii[y0 * ww + x1] - ii[y1 * ww + x0] + ii[y0 * ww + x0]

    /** Keeps a pixel only if the whole k x k square (k odd) around it is set. */
    fun erode(m: ByteArray, w: Int, h: Int, k: Int): ByteArray {
        val r = k / 2
        val ii = integral(m, w, h)
        val out = ByteArray(m.size)
        for (y in r until h - r) for (x in r until w - r) {
            if (windowSum(ii, w + 1, x - r, y - r, x + r + 1, y + r + 1) == k * k) out[y * w + x] = 1
        }
        return out
    }

    /** Sets a pixel if anything in the k x k square around it is set. */
    fun dilate(m: ByteArray, w: Int, h: Int, k: Int): ByteArray {
        val r = k / 2
        val ii = integral(m, w, h)
        val out = ByteArray(m.size)
        for (y in 0 until h) for (x in 0 until w) {
            val x0 = maxOf(0, x - r); val y0 = maxOf(0, y - r)
            val x1 = minOf(w, x + r + 1); val y1 = minOf(h, y + r + 1)
            if (windowSum(ii, w + 1, x0, y0, x1, y1) > 0) out[y * w + x] = 1
        }
        return out
    }

    /** Opening removes everything thinner than k pixels (lines, borders, rings) and keeps filled discs. */
    fun open(m: ByteArray, w: Int, h: Int, k: Int): ByteArray = dilate(erode(m, w, h, k), w, h, k)
}

class Blob(val area: Int, val cx: Double, val cy: Double, val minX: Int, val maxX: Int, val minY: Int, val maxY: Int) {
    val width get() = maxX - minX + 1
    val height get() = maxY - minY + 1
    /** Pixels set / bounding box: pi/4 = 0.785 for a disc. */
    val fill get() = area.toDouble() / (width.toDouble() * height)
}

/** 4-connected components of a binary mask. */
fun connectedComponents(m: ByteArray, w: Int, h: Int, minArea: Int): List<Blob> {
    val seen = BooleanArray(m.size)
    val stack = IntArray(m.size)
    val blobs = ArrayList<Blob>()
    for (start in m.indices) {
        if (m[start].toInt() == 0 || seen[start]) continue
        var sp = 0
        stack[sp++] = start
        seen[start] = true
        var area = 0
        var sx = 0.0; var sy = 0.0
        var minX = Int.MAX_VALUE; var maxX = -1; var minY = Int.MAX_VALUE; var maxY = -1
        while (sp > 0) {
            val p = stack[--sp]
            val x = p % w
            val y = p / w
            area++
            sx += x; sy += y
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            if (x > 0 && m[p - 1].toInt() != 0 && !seen[p - 1]) { seen[p - 1] = true; stack[sp++] = p - 1 }
            if (x < w - 1 && m[p + 1].toInt() != 0 && !seen[p + 1]) { seen[p + 1] = true; stack[sp++] = p + 1 }
            if (y > 0 && m[p - w].toInt() != 0 && !seen[p - w]) { seen[p - w] = true; stack[sp++] = p - w }
            if (y < h - 1 && m[p + w].toInt() != 0 && !seen[p + w]) { seen[p + w] = true; stack[sp++] = p + w }
        }
        if (area >= minArea) blobs.add(Blob(area, sx / area + 0.5, sy / area + 0.5, minX, maxX, minY, maxY))
    }
    return blobs
}
