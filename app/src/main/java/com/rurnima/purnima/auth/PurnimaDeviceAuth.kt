package com.rurnima.purnima.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import android.util.Base64

/**
 * Silent per-installation auth. The private key never leaves Android Keystore,
 * and access tokens are returned to the caller without being persisted.
 */
class PurnimaDeviceAuth(
    context: Context,
    private val baseUrl: String = AUTH_BASE_URL
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    suspend fun accessToken(): String = withContext(Dispatchers.IO) {
        val keyPair = loadOrCreateKeyPair()
        var deviceId = preferences.getString(DEVICE_ID, null)
        if (deviceId == null) {
            val response = post("/v1/device/register", JSONObject().put("publicKey", publicJwk(keyPair.public)))
            deviceId = response.getString("deviceId")
            preferences.edit().putString(DEVICE_ID, deviceId).apply()
        }

        val challengeResponse = post("/v1/device/challenge", JSONObject().put("deviceId", deviceId))
        val challenge = challengeResponse.getString("challenge")
        val timestamp = System.currentTimeMillis() / 1000L
        val message = "$deviceId.$challenge.$timestamp"
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(message.toByteArray(Charsets.UTF_8))
        val signature = base64Url(derToRaw(signer.sign()))

        val tokenResponse = post(
            "/v1/device/token",
            JSONObject()
                .put("deviceId", deviceId)
                .put("challenge", challenge)
                .put("timestamp", timestamp)
                .put("signature", signature)
        )
        tokenResponse.getString("accessToken")
    }

    private fun loadOrCreateKeyPair(): KeyPair {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null)
        if (existing != null) {
            val certificate = store.getCertificate(KEY_ALIAS)
            return KeyPair(certificate.publicKey, existing as java.security.PrivateKey)
        }
        // If Android has lost the key, do not reuse an old device ID.
        preferences.edit().remove(DEVICE_ID).apply()
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKeyPair()
    }

    private fun publicJwk(publicKey: java.security.PublicKey): JSONObject {
        val ec = publicKey as ECPublicKey
        return JSONObject()
            .put("kty", "EC")
            .put("crv", "P-256")
            .put("x", base64Url(unsigned32(ec.w.affineX)))
            .put("y", base64Url(unsigned32(ec.w.affineY)))
    }

    private fun unsigned32(value: BigInteger): ByteArray {
        val raw = value.toByteArray().let {
            if (it.size == 33 && it[0] == 0.toByte()) it.copyOfRange(1, 33) else it
        }
        require(raw.size <= 32) { "Invalid P-256 coordinate" }
        return ByteArray(32 - raw.size) + raw
    }

    /** Convert Java's ASN.1 DER ECDSA signature to WebCrypto's 64-byte P1363 form. */
    private fun derToRaw(der: ByteArray): ByteArray {
        require(der.size >= 8 && der[0] == 0x30.toByte()) { "Invalid ECDSA signature" }
        var offset = 2
        require(der[offset++] == 0x02.toByte()) { "Invalid ECDSA r" }
        val rLength = der[offset++].toInt() and 0xff
        val r = der.copyOfRange(offset, offset + rLength).dropLeadingZero()
        offset += rLength
        require(der[offset++] == 0x02.toByte()) { "Invalid ECDSA s" }
        val sLength = der[offset++].toInt() and 0xff
        val s = der.copyOfRange(offset, offset + sLength).dropLeadingZero()
        require(r.size <= 32 && s.size <= 32) { "Invalid ECDSA component length" }
        return ByteArray(32 - r.size) + r + ByteArray(32 - s.size) + s
    }

    private fun ByteArray.dropLeadingZero(): ByteArray =
        if (size > 1 && this[0] == 0.toByte()) copyOfRange(1, size) else this

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun post(path: String, body: JSONObject): JSONObject {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "Purnima-Android/0.1")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Purnima authentication failed ($status)")
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        // The app's Cloudflare Workers subdomain is provisioned for this project.
        const val AUTH_BASE_URL = "https://purnima-auth.purnima.workers.dev"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "purnima.device.identity.v1"
        private const val PREFERENCES = "purnima.device.auth"
        private const val DEVICE_ID = "device_id"
    }
}
