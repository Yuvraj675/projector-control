package dev.keystone.auto.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** A 2-D point (pixels). */
data class Pt(val x: Double, val y: Double) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Double) = Pt(x * k, y * k)
    fun dist(o: Pt) = hypot(x - o.x, y - o.y)
}

/** A 3-vector (used for directions in camera space). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun norm() = sqrt(dot(this))
    fun unit(): Vec3 {
        val n = norm()
        return Vec3(x / n, y / n, z / n)
    }
}

/** Row-major 3x3 matrix: homographies and rotations. */
class Mat3(val m: DoubleArray) {
    init {
        require(m.size == 9)
    }

    operator fun get(r: Int, c: Int) = m[r * 3 + c]

    operator fun times(o: Mat3): Mat3 {
        val out = DoubleArray(9)
        for (r in 0..2) for (c in 0..2) {
            var s = 0.0
            for (k in 0..2) s += this[r, k] * o[k, c]
            out[r * 3 + c] = s
        }
        return Mat3(out)
    }

    /** Applies the matrix as a homography (with the perspective divide). */
    fun apply(p: Pt): Pt {
        val x = this[0, 0] * p.x + this[0, 1] * p.y + this[0, 2]
        val y = this[1, 0] * p.x + this[1, 1] * p.y + this[1, 2]
        val w = this[2, 0] * p.x + this[2, 1] * p.y + this[2, 2]
        return Pt(x / w, y / w)
    }

    fun mul(v: Vec3) = Vec3(
        this[0, 0] * v.x + this[0, 1] * v.y + this[0, 2] * v.z,
        this[1, 0] * v.x + this[1, 1] * v.y + this[1, 2] * v.z,
        this[2, 0] * v.x + this[2, 1] * v.y + this[2, 2] * v.z,
    )

    fun inverse(): Mat3 {
        val a = m
        val det = a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6])
        require(abs(det) > 1e-300) { "singular matrix" }
        val inv = doubleArrayOf(
            (a[4] * a[8] - a[5] * a[7]), -(a[1] * a[8] - a[2] * a[7]), (a[1] * a[5] - a[2] * a[4]),
            -(a[3] * a[8] - a[5] * a[6]), (a[0] * a[8] - a[2] * a[6]), -(a[0] * a[5] - a[2] * a[3]),
            (a[3] * a[7] - a[4] * a[6]), -(a[0] * a[7] - a[1] * a[6]), (a[0] * a[4] - a[1] * a[3]),
        )
        return Mat3(DoubleArray(9) { inv[it] / det })
    }

    companion object {
        fun identity() = Mat3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))
        fun rows(r0: Vec3, r1: Vec3, r2: Vec3) = Mat3(doubleArrayOf(r0.x, r0.y, r0.z, r1.x, r1.y, r1.z, r2.x, r2.y, r2.z))
    }
}

/** Solves A x = b by Gaussian elimination with partial pivoting. */
fun solveLinear(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
    for (col in 0 until n) {
        var piv = col
        for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[piv][col])) piv = r
        require(abs(m[piv][col]) > 1e-12) { "singular system" }
        val t = m[col]; m[col] = m[piv]; m[piv] = t
        for (r in col + 1 until n) {
            val f = m[r][col] / m[col][col]
            for (c in col until n + 1) m[r][c] -= f * m[col][c]
        }
    }
    val x = DoubleArray(n)
    for (i in n - 1 downTo 0) {
        var s = m[i][n]
        for (j in i + 1 until n) s -= m[i][j] * x[j]
        x[i] = s / m[i][i]
    }
    return x
}

/** Homography mapping four source points exactly onto four destination points (h33 = 1). */
fun homography(src: List<Pt>, dst: List<Pt>): Mat3 {
    require(src.size == 4 && dst.size == 4)
    val a = Array(8) { DoubleArray(8) }
    val b = DoubleArray(8)
    for (i in 0 until 4) {
        val (x, y) = src[i]
        val (u, v) = dst[i]
        a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y)
        b[2 * i] = u
        a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y)
        b[2 * i + 1] = v
    }
    val h = solveLinear(a, b)
    return Mat3(doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0))
}

fun warp(h: Mat3, pts: List<Pt>): List<Pt> = pts.map { h.apply(it) }

/** Orders four points as TL, TR, BR, BL (valid for moderate tilt: uses x+y and y-x extremes). */
fun orderTlTrBrBl(pts: List<Pt>): List<Pt> {
    require(pts.size == 4)
    val tl = pts.minBy { it.x + it.y }
    val br = pts.maxBy { it.x + it.y }
    val tr = pts.minBy { it.y - it.x }
    val bl = pts.maxBy { it.y - it.x }
    return listOf(tl, tr, br, bl)
}

fun polygonArea(p: List<Pt>): Double {
    var s = 0.0
    for (i in p.indices) {
        val a = p[i]
        val b = p[(i + 1) % p.size]
        s += a.x * b.y - b.x * a.y
    }
    return abs(s) / 2.0
}

fun isConvex(p: List<Pt>): Boolean {
    var sign = 0
    for (i in p.indices) {
        val a = p[i]; val b = p[(i + 1) % p.size]; val c = p[(i + 2) % p.size]
        val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
        if (s != 0) {
            if (sign == 0) sign = s else if (sign != s) return false
        }
    }
    return true
}

/** How rectangular a TL,TR,BR,BL quadrilateral is. */
data class RectQuality(
    val tiltDeg: Double,        // worst edge angle away from its ideal axis
    val aspect: Double,         // mean width / mean height
    val topDeg: Double,         // signed angle of the top edge (clockwise positive, image y down)
    val bottomDeg: Double,
    val rightOverLeft: Double,  // right edge length / left edge length
)

fun rectQuality(q: List<Pt>): RectQuality {
    val (tl, tr, br, bl) = q
    val top = tr - tl; val bot = br - bl; val left = bl - tl; val right = br - tr
    val topA = Math.toDegrees(atan2(top.y, top.x))
    val botA = Math.toDegrees(atan2(bot.y, bot.x))
    val leftA = Math.toDegrees(atan2(left.x, left.y))
    val rightA = Math.toDegrees(atan2(right.x, right.y))
    val tilt = maxOf(abs(topA), abs(botA), abs(leftA), abs(rightA))
    val w = (hypot(top.x, top.y) + hypot(bot.x, bot.y)) / 2
    val h = (hypot(left.x, left.y) + hypot(right.x, right.y)) / 2
    return RectQuality(tilt, w / h, topA, botA, hypot(right.x, right.y) / hypot(left.x, left.y))
}
