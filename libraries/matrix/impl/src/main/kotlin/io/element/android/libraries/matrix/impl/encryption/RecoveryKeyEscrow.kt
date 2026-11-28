/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.encryption

import io.element.android.appconfig.LeveragersConfig
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.matrix.rustcomponents.sdk.Client
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

// Leveragers: a copy of every recovery key this app creates is sealed on the phone (Java's own crypto: one-time
// P-256 key pair, ECDH, HKDF-SHA-256, AES-256-GCM) to a public key whose private half only Leveragers holds
// (LeveragersConfig.RECOVERY_KEY_RECIPIENT), and sent to the homeserver, so that Leveragers can give it back to its
// owner. Same format (v1) and same route as our Element Web (recoveryKeyEscrow.ts).
//
// Never log the recovery key, the sealed copy nor the access token: HTTP statuses only.

private const val ESCROW_LOG_TAG = "RecoveryKeyEscrow"

/**
 * A sealed recovery key, format v1: base64url (unpadded) of the one-time P-256 public key (uncompressed point), of the
 * AES-GCM IV, and of the AES-GCM ciphertext followed by its tag.
 */
@Serializable
data class SealedRecoveryKey(
    val v: Int,
    val epk: String,
    val iv: String,
    val ct: String,
)

@Serializable
internal data class RecoveryKeyEscrowRequest(
    @SerialName("key_id") val keyId: String,
    val sealed: SealedRecoveryKey,
)

/**
 * Seals a recovery key to the recipient's P-256 public key, format v1: ECDH with a one-time P-256 key pair,
 * HKDF-SHA-256 (salt = one-time public key ‖ recipient public key, info `leveragers-recovery-key-v1`) to an AES-256-GCM
 * key, then AES-GCM with a random 12-byte IV and, as additional data, `<user_id>\n<key_id>`, so that a copy moved onto
 * another account or key does not open.
 */
object RecoveryKeySealer {
    const val FORMAT_VERSION = 1
    private const val INFO = "leveragers-recovery-key-v1"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val COORDINATE_LENGTH = 32
    private const val POINT_LENGTH = 1 + 2 * COORDINATE_LENGTH
    private const val UNCOMPRESSED_POINT_PREFIX: Byte = 0x04
    private const val AES_KEY_LENGTH = 32

    /** The domain parameters of P-256 (secp256r1), read from a key pair made by the platform. */
    val p256: ECParameterSpec by lazy { (newKeyPair().public as ECPublicKey).params }

    /** A new P-256 key pair. */
    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    /**
     * Seals [recoveryKey] (the text the SDK returns, `EsT…`) to [recipient] (base64url of an uncompressed P-256 point).
     * [ephemeral] and [iv] are for tests only: by default a new key pair and a random IV are used for each copy.
     * Throws if [recipient] is not a P-256 public key.
     */
    fun seal(
        recoveryKey: String,
        recipient: String,
        userId: String,
        keyId: String,
        ephemeral: KeyPair? = null,
        iv: ByteArray? = null,
    ): SealedRecoveryKey {
        val recipientBytes = decodeBase64Url(recipient)
        val recipientKey = decodePublicKey(recipientBytes)
        val oneTime = ephemeral ?: newKeyPair()
        val oneTimePublic = encodePublicKey(oneTime.public as ECPublicKey)
        val sharedSecret = KeyAgreement.getInstance("ECDH").run {
            init(oneTime.private)
            doPhase(recipientKey, true)
            generateSecret()
        }.toFixedLength(COORDINATE_LENGTH)
        val aesKey = hkdfSha256(
            ikm = sharedSecret,
            salt = oneTimePublic + recipientBytes,
            info = INFO.toByteArray(Charsets.UTF_8),
            length = AES_KEY_LENGTH,
        )
        val nonce = iv ?: ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        require(nonce.size == IV_LENGTH) { "IV of $IV_LENGTH bytes expected" }
        val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
            updateAAD(additionalData(userId, keyId))
            doFinal(recoveryKey.toByteArray(Charsets.UTF_8))
        }
        return SealedRecoveryKey(
            v = FORMAT_VERSION,
            epk = encodeBase64Url(oneTimePublic),
            iv = encodeBase64Url(nonce),
            ct = encodeBase64Url(ciphertext),
        )
    }

    /** The additional data of AES-GCM: UTF-8 of `<user_id>\n<key_id>`. */
    fun additionalData(userId: String, keyId: String): ByteArray = "$userId\n$keyId".toByteArray(Charsets.UTF_8)

    /** A P-256 public key from its uncompressed point (`04‖X‖Y`, 65 bytes). */
    fun decodePublicKey(point: ByteArray): PublicKey {
        require(point.size == POINT_LENGTH && point[0] == UNCOMPRESSED_POINT_PREFIX) { "uncompressed P-256 point expected" }
        val x = BigInteger(1, point.copyOfRange(1, 1 + COORDINATE_LENGTH))
        val y = BigInteger(1, point.copyOfRange(1 + COORDINATE_LENGTH, POINT_LENGTH))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), p256))
    }

    /** The uncompressed point (`04‖X‖Y`, 65 bytes) of a P-256 public key. */
    fun encodePublicKey(key: ECPublicKey): ByteArray =
        byteArrayOf(UNCOMPRESSED_POINT_PREFIX) +
            key.w.affineX.toByteArray().toFixedLength(COORDINATE_LENGTH) +
            key.w.affineY.toByteArray().toFixedLength(COORDINATE_LENGTH)

    /** HKDF-SHA-256 (RFC 5869) for an output of at most 32 bytes: one extract, one expand block. */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..32) { "at most one HKDF-SHA-256 block" }
        val prk = hmacSha256(key = salt, data = ikm)
        return hmacSha256(key = prk, data = info + byteArrayOf(1)).copyOf(length)
    }

    /** Base64url without padding. */
    fun encodeBase64Url(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes).trimEnd('=')

    /** Base64url, with or without padding. */
    fun decodeBase64Url(text: String): ByteArray {
        val unpadded = text.trim().trimEnd('=')
        return Base64.UrlSafe.decode(unpadded + "=".repeat((4 - unpadded.length % 4) % 4))
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }

    /** Left-pads with zeros, or drops the leading zeros a [BigInteger] adds for its sign. */
    private fun ByteArray.toFixedLength(length: Int): ByteArray = when {
        size == length -> this
        size > length -> copyOfRange(size - length, size)
        else -> ByteArray(length - size) + this
    }
}

/** An answer of the homeserver: its status and, for a 200, its body. */
data class EscrowHttpResponse(val status: Int, val body: String?)

/** The two requests the copy needs, behind an interface so that tests use a fake server. */
interface EscrowHttp {
    /** GET with the access token. Throws an [IOException] on network errors and timeouts. */
    suspend fun get(url: String, accessToken: String): EscrowHttpResponse

    /** POST of a JSON body with the access token, returns the status. Throws an [IOException] on network errors and timeouts. */
    suspend fun post(url: String, accessToken: String, jsonBody: String): Int
}

/** [EscrowHttp] with [HttpURLConnection], not the app's HTTP client, whose logs could show the access token. */
class UrlConnectionEscrowHttp(
    private val timeoutMillis: Int = RecoveryKeyEscrow.REQUEST_TIMEOUT_MILLIS,
) : EscrowHttp {
    override suspend fun get(url: String, accessToken: String): EscrowHttpResponse = withContext(Dispatchers.IO) {
        val connection = open(url, accessToken)
        try {
            val status = connection.responseCode
            val body = if (status == HttpURLConnection.HTTP_OK) connection.inputStream.bufferedReader().use { it.readText() } else null
            EscrowHttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun post(url: String, accessToken: String, jsonBody: String): Int = withContext(Dispatchers.IO) {
        val connection = open(url, accessToken)
        try {
            val bytes = jsonBody.toByteArray(Charsets.UTF_8)
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, accessToken: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            useCaches = false
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
}

/**
 * Sends a sealed copy of a recovery key the SDK has just created to `POST <homeserver>/_leveragers/keys/v1/recovery-key`,
 * with the id of the account's default secret storage key read from the homeserver (the SDK has written it before
 * returning the key; the homeserver checks it is the current one). Retries on network errors, timeouts, 429 and 5xx
 * after 2 s, 5 s, 15 s, 30 s, then every 60 s, for as long as the session runs; gives up on any other answer.
 *
 * @param accessToken read before each attempt: the SDK may have refreshed the token in the meantime.
 */
class RecoveryKeyEscrow(
    private val recipient: String,
    homeserverUrl: String,
    private val userId: String,
    private val accessToken: suspend () -> String?,
    private val http: EscrowHttp = UrlConnectionEscrowHttp(),
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val baseUrl = homeserverUrl.trimEnd('/')

    /** Where the copy goes. */
    val escrowUrl = "$baseUrl$ESCROW_PATH"

    /** Where the id of the account's default secret storage key is read. */
    val defaultKeyUrl = "$baseUrl/_matrix/client/v3/user/${encodePathSegment(userId)}/account_data/$DEFAULT_KEY_EVENT_TYPE"

    /**
     * Seals and sends [recoveryKey]. Does nothing without a recipient or with an empty key (a passphrase-based setup).
     * Never throws, except to cancel with the session.
     */
    suspend fun escrow(recoveryKey: String) {
        if (recipient.isBlank() || recoveryKey.isEmpty()) return
        try {
            send(recoveryKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(ESCROW_LOG_TAG).w("Recovery key copy not sent: ${e.javaClass.simpleName}")
        }
    }

    private sealed interface Outcome {
        data object Stored : Outcome
        data object GaveUp : Outcome
        data class Retry(val reason: String) : Outcome
    }

    private suspend fun send(recoveryKey: String) {
        // Sealed once, with the key id read once: a later attempt never ties this key to a newer key id.
        var body: String? = null

        suspend fun attemptWith(token: String): Outcome {
            val requestBody = body ?: when (val answer = readKeyId(token)) {
                is KeyIdAnswer.Found -> requestBody(recoveryKey, answer.keyId).also { body = it }
                is KeyIdAnswer.Failed -> return answer.outcome
            }
            return outcomeOfPost(http.post(escrowUrl, token, requestBody))
        }

        var attempt = 0
        while (true) {
            val token = accessToken()
            if (token.isNullOrEmpty()) {
                Timber.tag(ESCROW_LOG_TAG).w("No access token: recovery key copy not sent")
                return
            }
            val outcome = try {
                attemptWith(token)
            } catch (e: IOException) {
                Outcome.Retry("network error (${e.javaClass.simpleName})")
            }
            when (outcome) {
                Outcome.Stored, Outcome.GaveUp -> return
                is Outcome.Retry -> {
                    val delayMillis = RETRY_DELAYS_MILLIS.getOrElse(attempt) { LATER_RETRY_DELAY_MILLIS }
                    Timber.tag(ESCROW_LOG_TAG).i("Recovery key copy not sent (${outcome.reason}), next attempt in ${delayMillis / 1000} s")
                    sleep(delayMillis)
                    attempt++
                }
            }
        }
    }

    private sealed interface KeyIdAnswer {
        data class Found(val keyId: String) : KeyIdAnswer
        data class Failed(val outcome: Outcome) : KeyIdAnswer
    }

    private suspend fun readKeyId(token: String): KeyIdAnswer {
        val response = http.get(defaultKeyUrl, token)
        return when {
            response.status == HttpURLConnection.HTTP_OK -> {
                val keyId = keyIdFrom(response.body)
                if (keyId == null) {
                    Timber.tag(ESCROW_LOG_TAG).w("No default secret storage key: recovery key copy not sent")
                    KeyIdAnswer.Failed(Outcome.GaveUp)
                } else {
                    KeyIdAnswer.Found(keyId)
                }
            }
            isRetryable(response.status) -> KeyIdAnswer.Failed(Outcome.Retry("HTTP ${response.status} reading the key id"))
            else -> {
                Timber.tag(ESCROW_LOG_TAG).w("Key id not readable (HTTP ${response.status}): recovery key copy not sent")
                KeyIdAnswer.Failed(Outcome.GaveUp)
            }
        }
    }

    private fun requestBody(recoveryKey: String, keyId: String): String {
        val sealed = RecoveryKeySealer.seal(recoveryKey = recoveryKey, recipient = recipient, userId = userId, keyId = keyId)
        return Json.encodeToString(RecoveryKeyEscrowRequest.serializer(), RecoveryKeyEscrowRequest(keyId = keyId, sealed = sealed))
    }

    private fun outcomeOfPost(status: Int): Outcome = when {
        status in 200..299 -> Outcome.Stored.also { Timber.tag(ESCROW_LOG_TAG).i("Recovery key copy stored (HTTP $status)") }
        isRetryable(status) -> Outcome.Retry("HTTP $status")
        else -> Outcome.GaveUp.also { Timber.tag(ESCROW_LOG_TAG).w("Recovery key copy refused (HTTP $status): giving up") }
    }

    private fun isRetryable(status: Int) = status == HTTP_TOO_MANY_REQUESTS || status >= HttpURLConnection.HTTP_INTERNAL_ERROR

    companion object {
        /** Path, on the homeserver's base URL, of the route that stores the sealed copy. */
        const val ESCROW_PATH = "/_leveragers/keys/v1/recovery-key"
        const val DEFAULT_KEY_EVENT_TYPE = "m.secret_storage.default_key"

        /** Waits between the attempts, then [LATER_RETRY_DELAY_MILLIS] between all later ones. */
        val RETRY_DELAYS_MILLIS = listOf(2_000L, 5_000L, 15_000L, 30_000L)
        const val LATER_RETRY_DELAY_MILLIS = 60_000L
        const val REQUEST_TIMEOUT_MILLIS = 30_000
        private const val HTTP_TOO_MANY_REQUESTS = 429

        /** The `key` of an `m.secret_storage.default_key` account data event, or null. */
        fun keyIdFrom(body: String?): String? = runCatchingExceptions {
            Json.parseToJsonElement(body.orEmpty()).jsonObject["key"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotEmpty() }

        private fun encodePathSegment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}

/**
 * Sends, in the background of the session, a copy of the recovery keys that [RustEncryptionService] creates: nothing
 * waits for it and a failure never reaches the screen. Does nothing when [recipient] is empty.
 */
internal class RecoveryKeyEscrowLauncher(
    private val client: Client,
    private val sessionCoroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val recipient: String = LeveragersConfig.RECOVERY_KEY_RECIPIENT,
) {
    fun send(recoveryKey: String) {
        if (recipient.isBlank() || recoveryKey.isEmpty()) return
        sessionCoroutineScope.launch(ioDispatcher) {
            val escrow = runCatchingExceptions {
                RecoveryKeyEscrow(
                    recipient = recipient,
                    homeserverUrl = client.homeserver(),
                    userId = client.userId(),
                    accessToken = { runCatchingExceptions { client.session().accessToken }.getOrNull() },
                )
            }.getOrElse {
                Timber.tag(ESCROW_LOG_TAG).w("Recovery key copy not sent: ${it.javaClass.simpleName}")
                return@launch
            }
            escrow.escrow(recoveryKey)
        }
    }
}
