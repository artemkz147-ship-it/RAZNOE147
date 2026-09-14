package com.partymotion.playground

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.sqrt

data class ObjMesh(val interleaved: FloatArray) {
    val vertexCount: Int get() = interleaved.size / 6

    companion object {
        fun parse(input: InputStream): ObjMesh {
            val positions = mutableListOf<FloatArray>()
            val normals = mutableListOf<FloatArray>()
            val out = ArrayList<Float>(4096)
            BufferedReader(InputStreamReader(input)).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    when {
                        line.startsWith("v ") -> {
                            val p = line.split(Regex("\\s+"))
                            if (p.size >= 4) positions += floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
                        }
                        line.startsWith("vn ") -> {
                            val p = line.split(Regex("\\s+"))
                            if (p.size >= 4) normals += normalize(floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat()))
                        }
                        line.startsWith("f ") -> {
                            val tokens = line.substring(2).trim().split(Regex("\\s+"))
                            if (tokens.size < 3) return@forEach
                            val refs = tokens.mapNotNull { token -> parseRef(token, positions.size, normals.size) }
                            if (refs.size < 3) return@forEach
                            for (i in 1 until refs.lastIndex) emitTriangle(refs[0], refs[i], refs[i + 1], positions, normals, out)
                        }
                    }
                }
            }
            return ObjMesh(FloatArray(out.size) { out[it] })
        }

        private data class Ref(val v: Int, val n: Int?)

        private fun parseRef(token: String, vertexCount: Int, normalCount: Int): Ref? {
            val parts = token.split('/')
            val rawV = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val v = resolve(rawV, vertexCount) ?: return null
            val rawN = parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull()
            val n = rawN?.let { resolve(it, normalCount) }
            return Ref(v, n)
        }

        private fun resolve(index: Int, count: Int): Int? {
            val resolved = if (index > 0) index - 1 else count + index
            return resolved.takeIf { it in 0 until count }
        }

        private fun emitTriangle(a: Ref, b: Ref, c: Ref, positions: List<FloatArray>, normals: List<FloatArray>, out: MutableList<Float>) {
            val pa = positions[a.v]; val pb = positions[b.v]; val pc = positions[c.v]
            val face = faceNormal(pa, pb, pc)
            arrayOf(a, b, c).forEach { ref ->
                val p = positions[ref.v]
                val n = ref.n?.let { normals[it] } ?: face
                out += p[0]; out += p[1]; out += p[2]
                out += n[0]; out += n[1]; out += n[2]
            }
        }

        private fun faceNormal(a: FloatArray, b: FloatArray, c: FloatArray): FloatArray {
            val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
            val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
            return normalize(floatArrayOf(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx))
        }

        private fun normalize(v: FloatArray): FloatArray {
            val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-8f)
            return floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
        }
    }
}
