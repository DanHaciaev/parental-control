package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.Family
import com.teo.core.util.PinHasher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun familiesRef() = firestore.collection(FirestorePaths.FAMILIES)

    suspend fun createFamily(parentUid: String, parentEmail: String, timezone: String): String {
        val doc = familiesRef().document()
        val family = Family(
            id = doc.id,
            parentUid = parentUid,
            parentEmail = parentEmail,
            timezone = timezone
        )
        doc.set(family).await()
        return doc.id
    }

    /** Lets a parent recover their family after reinstalling/switching phones, keyed by their uid. */
    suspend fun findFamilyIdForParent(parentUid: String): String? {
        val snapshot = familiesRef().whereEqualTo("parentUid", parentUid).limit(1).get().await()
        return snapshot.documents.firstOrNull()?.id
    }

    suspend fun getFamily(familyId: String): Family? =
        familiesRef().document(familyId).get().await().toObject(Family::class.java)

    fun observeFamily(familyId: String): Flow<Family?> = callbackFlow {
        val registration = familiesRef().document(familyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(Family::class.java))
        }
        awaitClose { registration.remove() }
    }

    suspend fun setProtectionPin(familyId: String, pin: String) {
        val hashed = PinHasher.hash(pin)
        familiesRef().document(familyId)
            .update(
                mapOf(
                    "protectionPinHash" to hashed.hash,
                    "protectionPinSalt" to hashed.salt
                )
            ).await()
    }

    suspend fun verifyProtectionPin(familyId: String, pin: String): Boolean {
        val family = getFamily(familyId) ?: return false
        if (family.protectionPinHash.isEmpty()) return false
        return PinHasher.verify(pin, family.protectionPinHash, family.protectionPinSalt)
    }

    suspend fun setChildDeviceName(familyId: String, deviceName: String) {
        familiesRef().document(familyId).update("childDeviceName", deviceName).await()
    }

    suspend fun setParentPhone(familyId: String, phone: String) {
        familiesRef().document(familyId).update("parentPhone", phone).await()
    }

    suspend fun setBedtime(familyId: String, startMinutes: Int?, endMinutes: Int?) {
        familiesRef().document(familyId).update(
            mapOf(
                "bedtimeStartMinutes" to startMinutes,
                "bedtimeEndMinutes" to endMinutes
            )
        ).await()
    }
}
