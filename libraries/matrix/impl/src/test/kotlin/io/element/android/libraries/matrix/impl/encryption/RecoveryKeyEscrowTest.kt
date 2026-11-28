/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.encryption

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.spec.ECPrivateKeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Leveragers: sealing (format v1, the shared test vector of the plan, opened by hub2's reader `tools/recovery_key.py`)
 * and sending of the recovery key copy (RecoveryKeyEscrow.kt).
 */
class RecoveryKeyEscrowTest {
    @Test
    fun sealMatchesVector() {
        val sealed = RecoveryKeySealer.seal(
            recoveryKey = VECTOR_RECOVERY_KEY,
            recipient = VECTOR_RECIPIENT_PUBLIC,
            userId = VECTOR_USER_ID,
            keyId = VECTOR_KEY_ID,
            ephemeral = KeyPair(
                RecoveryKeySealer.decodePublicKey(b64(VECTOR_EPHEMERAL_PUBLIC)),
                privateKey(VECTOR_EPHEMERAL_PRIVATE),
            ),
            iv = b64(VECTOR_IV),
        )
        assertThat(sealed).isEqualTo(
            SealedRecoveryKey(v = 1, epk = VECTOR_EPHEMERAL_PUBLIC, iv = VECTOR_IV, ct = VECTOR_EXPECTED_CT)
        )
    }

    @Test
    fun sealOpensWithRecipientKey() {
        val sealed = RecoveryKeySealer.seal(VECTOR_RECOVERY_KEY, VECTOR_RECIPIENT_PUBLIC, VECTOR_USER_ID, VECTOR_KEY_ID)
        assertThat(sealed.v).isEqualTo(1)
        assertThat(b64(sealed.epk)).hasLength(65)
        assertThat(b64(sealed.iv)).hasLength(12)
        assertThat(sealed.ct).doesNotContain("=")
        assertThat(open(sealed, VECTOR_USER_ID, VECTOR_KEY_ID)).isEqualTo(VECTOR_RECOVERY_KEY)
        // Moved onto another account or another key: the copy does not open.
        assertThrows(AEADBadTagException::class.java) { open(sealed, "@someone-else:chat.leveragers.com", VECTOR_KEY_ID) }
        assertThrows(AEADBadTagException::class.java) { open(sealed, VECTOR_USER_ID, "AnotherKeyId") }
        // A new one-time key and a new IV for each copy.
        val other = RecoveryKeySealer.seal(VECTOR_RECOVERY_KEY, VECTOR_RECIPIENT_PUBLIC, VECTOR_USER_ID, VECTOR_KEY_ID)
        assertThat(other.epk).isNotEqualTo(sealed.epk)
        assertThat(other.iv).isNotEqualTo(sealed.iv)
    }

    @Test
    fun hkdfMatchesRfc5869() {
        // RFC 5869, test case 1 (first 32 bytes of the output).
        val okm = RecoveryKeySealer.hkdfSha256(
            ikm = ByteArray(22) { 0x0b },
            salt = ByteArray(13) { it.toByte() },
            info = ByteArray(10) { (0xf0 + it).toByte() },
            length = 32,
        )
        assertThat(okm.toHex()).isEqualTo("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf")
    }

    @Test
    fun keyIdReadFromServer() = runTest {
        val http = FakeEscrowHttp(getAnswer = { EscrowHttpResponse(200, """{"key":"KeyIdFromTheServer"}""") }, postStatuses = listOf(201))
        val escrow = anEscrow(http = http, homeserverUrl = "https://chat.leveragers.com/")
        escrow.escrow(VECTOR_RECOVERY_KEY)
        assertThat(http.gets).containsExactly(
            "https://chat.leveragers.com/_matrix/client/v3/user/%40essai-vecteur%3Achat.leveragers.com/account_data/m.secret_storage.default_key" to "token-1"
        )
        val post = http.posts.single()
        assertThat(post.url).isEqualTo("https://chat.leveragers.com/_leveragers/keys/v1/recovery-key")
        val body = Json.parseToJsonElement(post.body).jsonObject
        assertThat(body["key_id"]!!.jsonPrimitive.content).isEqualTo("KeyIdFromTheServer")
        val sealedJson = body["sealed"]!!.jsonObject
        assertThat(sealedJson["v"]!!.jsonPrimitive.int).isEqualTo(1)
        val sealed = SealedRecoveryKey(
            v = 1,
            epk = sealedJson["epk"]!!.jsonPrimitive.content,
            iv = sealedJson["iv"]!!.jsonPrimitive.content,
            ct = sealedJson["ct"]!!.jsonPrimitive.content,
        )
        // Sealed for the key id read from the server.
        assertThat(open(sealed, VECTOR_USER_ID, "KeyIdFromTheServer")).isEqualTo(VECTOR_RECOVERY_KEY)
    }

    @Test
    fun retriesThen201() = runTest {
        val http = FakeEscrowHttp(postStatuses = listOf(503, 201))
        val sleeps = mutableListOf<Long>()
        anEscrow(http = http, sleeps = sleeps).escrow(VECTOR_RECOVERY_KEY)
        assertThat(http.posts).hasSize(2)
        assertThat(sleeps).containsExactly(2_000L)
        // The key id is read once, and the same copy is sent again.
        assertThat(http.gets).hasSize(1)
        assertThat(http.posts[1].body).isEqualTo(http.posts[0].body)
    }

    @Test
    fun retrySchedule() = runTest {
        val http = FakeEscrowHttp(postStatuses = listOf(503, 502, 429, 500, 504, 503, 201))
        val sleeps = mutableListOf<Long>()
        anEscrow(http = http, sleeps = sleeps).escrow(VECTOR_RECOVERY_KEY)
        assertThat(http.posts).hasSize(7)
        assertThat(sleeps).containsExactly(2_000L, 5_000L, 15_000L, 30_000L, 60_000L, 60_000L).inOrder()
    }

    @Test
    fun networkErrorIsRetried() = runTest {
        var calls = 0
        val http = FakeEscrowHttp(postAnswer = { if (calls++ == 0) throw IOException("timeout") else 201 })
        val sleeps = mutableListOf<Long>()
        anEscrow(http = http, sleeps = sleeps).escrow(VECTOR_RECOVERY_KEY)
        assertThat(http.posts).hasSize(2)
        assertThat(sleeps).containsExactly(2_000L)
    }

    @Test
    fun givesUpOn401() = runTest {
        val http = FakeEscrowHttp(postStatuses = listOf(401, 201))
        val sleeps = mutableListOf<Long>()
        anEscrow(http = http, sleeps = sleeps).escrow(VECTOR_RECOVERY_KEY)
        assertThat(http.posts).hasSize(1)
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun givesUpOn404() = runTest {
        // A homeserver without the route: the app behaves like Element X.
        val http = FakeEscrowHttp(postStatuses = listOf(404, 201))
        val sleeps = mutableListOf<Long>()
        anEscrow(http = http, sleeps = sleeps).escrow(VECTOR_RECOVERY_KEY)
        assertThat(http.posts).hasSize(1)
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun givesUpOn400() = runTest {
        // The key id is not the account's current one any more.
        val http = FakeEscrowHttp(postStatuses = listOf(400, 201))
        anEscrow(http = http).escrow(VECTOR_RECOVERY_KEY)
        assertThat(http.posts).hasSize(1)
    }

    @Test
    fun noSendWithoutKeyId() = runTest {
        for (answer in listOf(EscrowHttpResponse(404, null), EscrowHttpResponse(200, "{}"), EscrowHttpResponse(200, "not json"))) {
            val http = FakeEscrowHttp(getAnswer = { answer }, postStatuses = listOf(201))
            val sleeps = mutableListOf<Long>()
            anEscrow(http = http, sleeps = sleeps).escrow(VECTOR_RECOVERY_KEY)
            assertThat(http.gets).hasSize(1)
            assertThat(http.posts).isEmpty()
            assertThat(sleeps).isEmpty()
        }
    }

    @Test
    fun keyIdReadRetriedOn5xx() = runTest {
        var gets = 0
        val http = FakeEscrowHttp(
            getAnswer = { if (gets++ == 0) EscrowHttpResponse(502, null) else EscrowHttpResponse(200, """{"key":"$VECTOR_KEY_ID"}""") },
            postStatuses = listOf(201),
        )
        val sleeps = mutableListOf<Long>()
        anEscrow(http = http, sleeps = sleeps).escrow(VECTOR_RECOVERY_KEY)
        assertThat(http.gets).hasSize(2)
        assertThat(http.posts).hasSize(1)
        assertThat(sleeps).containsExactly(2_000L)
    }

    @Test
    fun tokenReadPerAttempt() = runTest {
        val http = FakeEscrowHttp(postStatuses = listOf(500, 429, 201))
        var tokenReads = 0
        val escrow = anEscrow(http = http, accessToken = { "token-${++tokenReads}" })
        escrow.escrow(VECTOR_RECOVERY_KEY)
        assertThat(tokenReads).isEqualTo(3)
        assertThat(http.posts.map { it.token }).containsExactly("token-1", "token-2", "token-3").inOrder()
    }

    @Test
    fun noSendWithoutToken() = runTest {
        val http = FakeEscrowHttp(postStatuses = listOf(201))
        anEscrow(http = http, accessToken = { null }).escrow(VECTOR_RECOVERY_KEY)
        assertThat(http.gets).isEmpty()
        assertThat(http.posts).isEmpty()
    }

    @Test
    fun emptyRecoveryKeyNotSent() = runTest {
        val http = FakeEscrowHttp(postStatuses = listOf(201))
        anEscrow(http = http).escrow("")
        assertThat(http.gets).isEmpty()
        assertThat(http.posts).isEmpty()
    }

    @Test
    fun emptyRecipientIsNoOp() = runTest {
        val http = FakeEscrowHttp(postStatuses = listOf(201))
        anEscrow(http = http, recipient = "").escrow(VECTOR_RECOVERY_KEY)
        assertThat(http.gets).isEmpty()
        assertThat(http.posts).isEmpty()
    }

    @Test
    fun neverThrows() = runTest {
        val http = FakeEscrowHttp(getAnswer = { error("unexpected") })
        anEscrow(http = http).escrow(VECTOR_RECOVERY_KEY)
        anEscrow(http = FakeEscrowHttp(postStatuses = listOf(201)), recipient = "not a key").escrow(VECTOR_RECOVERY_KEY)
    }

    private fun anEscrow(
        http: EscrowHttp,
        recipient: String = VECTOR_RECIPIENT_PUBLIC,
        homeserverUrl: String = "https://chat.leveragers.com",
        accessToken: suspend () -> String? = { "token-1" },
        sleeps: MutableList<Long> = mutableListOf(),
    ) = RecoveryKeyEscrow(
        recipient = recipient,
        homeserverUrl = homeserverUrl,
        userId = VECTOR_USER_ID,
        accessToken = accessToken,
        http = http,
        sleep = { sleeps += it },
    )

    private class FakeEscrowHttp(
        private val getAnswer: (String) -> EscrowHttpResponse = { EscrowHttpResponse(200, """{"key":"$VECTOR_KEY_ID"}""") },
        postStatuses: List<Int> = emptyList(),
        private val postAnswer: (Int) -> Int = { index -> postStatuses.getOrElse(index) { error("unexpected POST") } },
    ) : EscrowHttp {
        data class Post(val url: String, val token: String, val body: String)

        val gets = mutableListOf<Pair<String, String>>()
        val posts = mutableListOf<Post>()

        override suspend fun get(url: String, accessToken: String): EscrowHttpResponse {
            gets += url to accessToken
            return getAnswer(url)
        }

        override suspend fun post(url: String, accessToken: String, jsonBody: String): Int {
            posts += Post(url, accessToken, jsonBody)
            return postAnswer(posts.size - 1)
        }
    }

    /** Opens a copy as hub2's reader does (tools/recovery_key.py, `unseal`), with the lab recipient private key. */
    private fun open(sealed: SealedRecoveryKey, userId: String, keyId: String): String {
        val oneTimePublic = b64(sealed.epk)
        val sharedSecret = KeyAgreement.getInstance("ECDH").run {
            init(privateKey(VECTOR_RECIPIENT_PRIVATE))
            doPhase(RecoveryKeySealer.decodePublicKey(oneTimePublic), true)
            generateSecret()
        }
        val aesKey = RecoveryKeySealer.hkdfSha256(
            ikm = sharedSecret,
            salt = oneTimePublic + b64(VECTOR_RECIPIENT_PUBLIC),
            info = "leveragers-recovery-key-v1".toByteArray(),
            length = 32,
        )
        val plaintext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, b64(sealed.iv)))
            updateAAD("$userId\n$keyId".toByteArray())
            doFinal(b64(sealed.ct))
        }
        return plaintext.toString(Charsets.UTF_8)
    }

    private fun privateKey(scalar: String): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, b64(scalar)), RecoveryKeySealer.p256))

    private fun b64(text: String) = RecoveryKeySealer.decodeBase64Url(text)

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {
        // LAB test vector of the plan (throwaway keys, not hub2's), made with Python's `cryptography` and opened by
        // hub2's reader tools/recovery_key.py (`unseal`).
        const val VECTOR_RECIPIENT_PRIVATE = "7hwlRi8RtZZJPkFwETuSRAx-qnp1p93xAVJ44IXZsj0"
        const val VECTOR_RECIPIENT_PUBLIC = "BJNxnRz4OxHM6yQnNKy4oGDPeIo-oKE6b-ELCmfN-b39QLBTJyiONkFkOFlHlyn6M2qwgEXxmKugjfmoD31mxbI"
        const val VECTOR_EPHEMERAL_PRIVATE = "UxAGd9G18GRUfvL5Nqmlk8uRqbittPiohFoBqTmDRco"
        const val VECTOR_EPHEMERAL_PUBLIC = "BLS1IKF5RcveUgaHXSEKgfOn4Y1TjaEgcsUzZFFh_nYf7fOOaR_pKndui89lE78G8eq8whEBnj7jUFMhVe3vkNc"
        const val VECTOR_IV = "AQIDBAUGBwgJCgsM"
        const val VECTOR_USER_ID = "@essai-vecteur:chat.leveragers.com"
        const val VECTOR_KEY_ID = "Kq7bT0aZ3c9XvLmN2pQrS4uW"
        const val VECTOR_RECOVERY_KEY = "EsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d"
        const val VECTOR_EXPECTED_CT =
            "Fyqgjg-NasDVDNAObD4wAS_lbuezjiibVv7KqJ6SE26DqORkO3NA5ExpqDMDQIWZYyTj7eqTeraSIRM23VNGn-3fevvz_1flFwJY"
    }
}
