package com.teo.core.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.teo.core.FirestorePaths
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

class PairingException(message: String) : Exception(message)

@Singleton
class PairingRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun codesRef() = firestore.collection(FirestorePaths.PAIRING_CODES)
    private fun familiesRef() = firestore.collection(FirestorePaths.FAMILIES)

    /** 6-digit numeric code, short-lived, read by exact id only (rules disallow listing). */
    suspend fun generateCode(familyId: String, validForMinutes: Long = 15): String {
        val code = Random.nextInt(100_000, 999_999).toString()
        val expiresAt = Date(System.currentTimeMillis() + validForMinutes * 60_000)
        codesRef().document(code).set(
            mapOf(
                "familyId" to familyId,
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to expiresAt,
                "used" to false
            )
        ).await()
        return code
    }

    /**
     * Claims a code for the given anonymous child uid; returns the resolved familyId.
     *
     * Deliberately does NOT read the family document first: an unlinked child isn't a family
     * member yet, so `families/{familyId}` isn't readable to them under the security rules
     * (correctly — that's what keeps other families' data private). The "already claimed" and
     * "family exists" checks are instead enforced purely by the security rules at write time
     * (`resource.data.childUid == null`) and by Firestore itself (update on a missing doc fails),
     * and this function just translates the resulting error codes into friendly messages.
     */
    suspend fun claimCode(code: String, childUid: String): String {
        val codeRef = codesRef().document(code)
        try {
            return firestore.runTransaction { transaction ->
                val codeSnapshot = transaction.get(codeRef)
                if (!codeSnapshot.exists()) throw PairingException("Код не найден")

                val used = codeSnapshot.getBoolean("used") ?: false
                if (used) throw PairingException("Код уже использован")

                val expiresAt = codeSnapshot.getDate("expiresAt")
                if (expiresAt == null || expiresAt.before(Date())) throw PairingException("Код истёк")

                val familyId = codeSnapshot.getString("familyId")
                    ?: throw PairingException("Некорректный код")
                val familyRef = familiesRef().document(familyId)

                transaction.update(familyRef, "childUid", childUid)
                transaction.update(codeRef, "used", true)
                familyId
            }.await()
        } catch (e: PairingException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            throw when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    PairingException("К этой семье уже привязано другое устройство")
                FirebaseFirestoreException.Code.NOT_FOUND ->
                    PairingException("Семья не найдена")
                else -> e
            }
        }
    }

    /**
     * Claims a code for a non-phone device (e.g. the Windows laptop client) — a sibling to
     * [claimCode], not a variant of it. Unlike childUid's single-slot claim, each device gets its
     * own doc keyed by its own uid, so there's no mutual-exclusion state to update atomically:
     * three sequential calls instead of a transaction is enough (worst case if step 3 fails after
     * step 2 succeeded, the device is already paired and the code just lingers reusable until its
     * 15-minute TTL — acceptable, same low-stakes tradeoff already accepted elsewhere here).
     */
    suspend fun claimCodeForDevice(code: String, deviceUid: String, label: String, type: String): String {
        val codeRef = codesRef().document(code)
        try {
            val codeSnapshot = codeRef.get().await()
            if (!codeSnapshot.exists()) throw PairingException("Код не найден")

            val used = codeSnapshot.getBoolean("used") ?: false
            if (used) throw PairingException("Код уже использован")

            val expiresAt = codeSnapshot.getDate("expiresAt")
            if (expiresAt == null || expiresAt.before(Date())) throw PairingException("Код истёк")

            val familyId = codeSnapshot.getString("familyId") ?: throw PairingException("Некорректный код")

            familiesRef().document(familyId).collection(FirestorePaths.DEVICES).document(deviceUid).set(
                mapOf(
                    "type" to type,
                    "label" to label,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            codeRef.update("used", true).await()
            return familyId
        } catch (e: PairingException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            throw when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    PairingException("Не удалось привязать устройство")
                FirebaseFirestoreException.Code.NOT_FOUND ->
                    PairingException("Семья не найдена")
                else -> e
            }
        }
    }
}
