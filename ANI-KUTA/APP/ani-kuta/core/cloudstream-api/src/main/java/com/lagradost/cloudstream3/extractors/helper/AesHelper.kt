// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — StreamPlay calls AesHelper.cryptoAESHandler (via its
// $default synthetic: (String, ByteArray, boolean, boolean) with both booleans
// defaulted) to decrypt the AES-CBC payloads many hosters embed as
// {"ct": …, "iv": …, "s": …} JSON. Upstream implements it over a multiplatform
// cryptography library; ours is a clean-room implementation on the JVM's own
// javax.crypto stack — the standard OpenSSL EVP_BytesToKey (MD5, one
// iteration) key/iv derivation the format is defined by, AES/CBC with an
// optional PKCS#5 padding, matching upstream's parameter semantics exactly.
package com.lagradost.cloudstream3.extractors.helper

import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-CBC helper for the {"ct","iv","s"} encrypted-payload format. */
object AesHelper {

    /** The wire format one payload carries. */
    @kotlinx.serialization.Serializable
    private data class AesData(
        @com.fasterxml.jackson.annotation.JsonProperty("ct") val ct: String,
        @com.fasterxml.jackson.annotation.JsonProperty("iv") val iv: String,
        @com.fasterxml.jackson.annotation.JsonProperty("s") val s: String,
    )

    /** The direct serializer route — no KClass reflection (the payload class is file-private). */
    private val aesJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * Decrypts (or encrypts) one payload.
     *
     * @param data the {"ct","iv","s"} JSON string.
     * @param pass the password bytes (the site's key).
     * @param encrypt false = decrypt (the hoster direction), true = encrypt.
     * @param padding false = stream-accurate no-padding mode.
     */
    suspend fun cryptoAESHandler(
        data: String,
        pass: ByteArray,
        encrypt: Boolean = true,
        padding: Boolean = true,
    ): String? {
        val parse = runCatching { aesJson.decodeFromString(AesData.serializer(), data) }.getOrNull()
            ?: return null
        val salt = parse.s.hexToByteArray()
        val (key, iv) = generateKeyAndIv(
            password = pass,
            salt = salt,
            keyLength = 32,
            ivLength = parse.iv.length / 2,
            saltLength = parse.s.length / 2,
        ) ?: return null

        val transformation = if (padding) "AES/CBC/PKCS5Padding" else "AES/CBC/NoPadding"
        val cipher = Cipher.getInstance(transformation)
        val secret = SecretKeySpec(key, "AES")
        return try {
            if (!encrypt) {
                cipher.init(Cipher.DECRYPT_MODE, secret, IvParameterSpec(iv))
                cipher.doFinal(base64DecodeArray(parse.ct)).decodeToString()
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secret, IvParameterSpec(iv))
                base64Encode(cipher.doFinal(parse.ct.encodeToByteArray()))
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The OpenSSL EVP_BytesToKey derivation (MD5, single iteration) — the
     * digest chain D_i = MD5(D_(i-1) ‖ password ‖ salt) until key+iv are
     * covered. Returns (key, iv) or null on failure.
     */
    fun generateKeyAndIv(
        password: ByteArray,
        salt: ByteArray,
        keyLength: Int = 32,
        ivLength: Int,
        saltLength: Int,
        iterations: Int = 1,
    ): Pair<ByteArray, ByteArray>? {
        return try {
            val digestLength = 16 // MD5 digests are always 16 bytes
            val targetKeySize = keyLength + ivLength
            val requiredLength = (targetKeySize + digestLength - 1) / digestLength * digestLength
            val generatedData = ByteArray(requiredLength)
            var generatedLength = 0

            while (generatedLength < targetKeySize) {
                val md = MessageDigest.getInstance("MD5")
                if (generatedLength > 0) {
                    // MessageDigest.update(buffer, offset, LENGTH) — the previous
                    // digest's 16 bytes lead the next round (EVP_BytesToKey chain).
                    md.update(generatedData, generatedLength - digestLength, digestLength)
                }
                md.update(password)
                md.update(salt, 0, saltLength)
                var digest = md.digest()
                digest.copyInto(generatedData, generatedLength)

                // Extra iterations hash the previous digest in place (EVP behavior).
                for (i in 1 until iterations) {
                    val iter = MessageDigest.getInstance("MD5")
                    iter.update(generatedData, generatedLength, digestLength)
                    digest = iter.digest()
                    digest.copyInto(generatedData, generatedLength)
                }
                generatedLength += digestLength
            }

            generatedData.copyOfRange(0, keyLength) to
                generatedData.copyOfRange(keyLength, targetKeySize)
        } catch (_: Exception) {
            null
        }
    }

    /** Hex decode (uppercase and lowercase digits). */
    private fun String.hexToByteArray(): ByteArray {
        val clean = if (length % 2 == 0) this else "0$this"
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
