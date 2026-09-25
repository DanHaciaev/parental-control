package com.teo.core.repository

import com.google.firebase.firestore.FieldValue
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

    /** Frees up the family's single childUid slot so a fresh pairing code can claim it again — the
     *  child-claims-a-code write (see PairingRepository.claimCode) is only allowed by the security
     *  rules while childUid is null, so a re-pair (e.g. the child app got uninstalled, which loses
     *  its anonymous auth uid along with it) needs the parent to clear the old one first. Parent-only
     *  in the rules, unconditionally — this is the one write path that can touch childUid besides the
     *  child's own one-time claim. */
    suspend fun resetChildPairing(familyId: String) {
        familiesRef().document(familyId).update("childUid", null).await()
    }

    suspend fun setChildDeviceName(familyId: String, deviceName: String) {
        familiesRef().document(familyId).update("childDeviceName", deviceName).await()
    }

    suspend fun setChildName(familyId: String, name: String) {
        familiesRef().document(familyId).update("childName", name).await()
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

    suspend fun setTotalScreenTimeCap(familyId: String, capMinutes: Int?) {
        familiesRef().document(familyId).update("totalScreenTimeCapMinutes", capMinutes).await()
    }

    suspend fun setFullBlockAllowedPackages(familyId: String, packages: List<String>) {
        familiesRef().document(familyId).update("fullBlockAllowedPackages", packages).await()
    }

    /** Sets a single weekday's override for the total cap without touching the other 6 — the family
     *  doc always exists by the time a parent can reach this screen, so (unlike RuleRepository's
     *  per-app equivalent) a direct dotted-path update is safe: no "document doesn't exist yet" case
     *  to worry about. [isoDayOfWeek] is 1=Monday..7=Sunday. */
    suspend fun setDayTotalCap(familyId: String, isoDayOfWeek: Int, minutes: Int) {
        familiesRef().document(familyId).update("weeklyTotalCapMinutes.$isoDayOfWeek", minutes).await()
    }

    suspend fun clearDayTotalCap(familyId: String, isoDayOfWeek: Int) {
        familiesRef().document(familyId).update("weeklyTotalCapMinutes.$isoDayOfWeek", FieldValue.delete()).await()
    }
}
