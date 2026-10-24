package io.github.teslablecontrol.protocol.crypto

import io.github.teslablecontrol.protocol.toFixedBytes
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

/** NIST P-256 helpers: point encoding/validation plus the curve arithmetic JCA doesn't expose. */
object P256 {
    const val FIELD_SIZE = 32
    const val UNCOMPRESSED_POINT_SIZE = 65

    private val P = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)
    private val A = P.subtract(BigInteger.valueOf(3))
    private val B = BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16)
    val N: BigInteger = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
    private val G = Point(
        BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
        BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
    )

    val parameterSpec: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }
    }

    data class Point(val x: BigInteger, val y: BigInteger)

    fun isOnCurve(x: BigInteger, y: BigInteger): Boolean {
        if (x.signum() < 0 || x >= P || y.signum() < 0 || y >= P) return false
        val lhs = y.multiply(y).mod(P)
        val rhs = x.multiply(x).multiply(x).add(A.multiply(x)).add(B).mod(P)
        return lhs == rhs
    }

    /** `0x04 || X || Y`, the encoding used for every public key in the protocol. */
    fun encode(point: Point): ByteArray =
        byteArrayOf(4) + point.x.toFixedBytes(FIELD_SIZE) + point.y.toFixedBytes(FIELD_SIZE)

    /** Parses and validates an uncompressed point. */
    fun decode(encoded: ByteArray): Point {
        require(encoded.size == UNCOMPRESSED_POINT_SIZE && encoded[0] == 4.toByte()) {
            "public key must be an uncompressed P-256 point (65 bytes starting with 0x04)"
        }
        val x = BigInteger(1, encoded.copyOfRange(1, 1 + FIELD_SIZE))
        val y = BigInteger(1, encoded.copyOfRange(1 + FIELD_SIZE, UNCOMPRESSED_POINT_SIZE))
        require(isOnCurve(x, y)) { "public key is not a point on P-256" }
        return Point(x, y)
    }

    fun isValidPublicKey(encoded: ByteArray): Boolean = runCatching { decode(encoded) }.isSuccess

    fun toJcaPublicKey(encoded: ByteArray): ECPublicKey {
        val point = decode(encoded)
        val spec = ECPublicKeySpec(ECPoint(point.x, point.y), parameterSpec)
        return KeyFactory.getInstance("EC").generatePublic(spec) as ECPublicKey
    }

    /** k·G, used to derive the public key of an imported private scalar. */
    fun multiplyGenerator(k: BigInteger): Point {
        require(k.signum() > 0 && k < N) { "private scalar out of range" }
        // Montgomery ladder: the same sequence of operations for every bit.
        var r0: Point? = null
        var r1: Point? = G
        for (i in k.bitLength() - 1 downTo 0) {
            if (k.testBit(i)) {
                r0 = add(r0, r1)
                r1 = add(r1, r1)
            } else {
                r1 = add(r0, r1)
                r0 = add(r0, r0)
            }
        }
        return requireNotNull(r0) { "scalar multiplication produced the point at infinity" }
    }

    // Affine arithmetic; null is the point at infinity. Only used for key import, so clarity wins over speed.
    private fun add(p: Point?, q: Point?): Point? {
        if (p == null) return q
        if (q == null) return p
        val lambda = if (p.x == q.x) {
            if (p.y.add(q.y).mod(P).signum() == 0) return null
            // Doubling: (3x² + a) / 2y
            p.x.multiply(p.x).multiply(BigInteger.valueOf(3)).add(A)
                .multiply(p.y.shiftLeft(1).modInverse(P)).mod(P)
        } else {
            q.y.subtract(p.y).multiply(q.x.subtract(p.x).modInverse(P)).mod(P)
        }
        val x3 = lambda.multiply(lambda).subtract(p.x).subtract(q.x).mod(P)
        val y3 = lambda.multiply(p.x.subtract(x3)).subtract(p.y).mod(P)
        return Point(x3, y3)
    }
}
