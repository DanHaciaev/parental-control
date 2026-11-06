package com.teo.core.repository

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.Family
import com.teo.core.util.PinHasher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) {
    private fun familiesRef() = firestore.collection(FirestorePaths.FAMILIES)

    /** Which child the parent app currently shows, per parent account. Device-local on purpose —
     *  two parents looking at different children at the same time is normal. */
    private val selectionPrefs by lazy {
        context.getSharedPreferences("family_selection", Context.MODE_PRIVATE)
    }

    suspend fun createFamily(parentUid: String, parentEmail: String, timezone: String): String {
        val doc = familiesRef().document()
        val family = Family(
            id = doc.id,
            parentUid = parentUid,
            parentUids = listOf(parentUid),
            parentEmail = parentEmail,
            timezone = timezone
        )
        doc.set(family).await()
        return doc.id
    }

    /**
     * Every child profile this parent manages, oldest first. Two queries because documents created
     * before co-parents existed only have [Family.parentUid]; those get [Family.parentUids] backfilled
     * here, since joining as a second parent (see PairingRepository.joinAsParent) appends to that list
     * and the security rules require it to already exist.
     */
    suspend fun familiesForParent(parentUid: String): List<Family> {
        val byList = familiesRef().whereArrayContains("parentUids", parentUid).get().await()
            .toObjects(Family::class.java)
        val legacy = familiesRef().whereEqualTo("parentUid", parentUid).get().await()
            .toObjects(Family::class.java)
            .filter { it.parentUids.isEmpty() }
        legacy.forEach { family ->
            runCatching { familiesRef().document(family.id).update("parentUids", listOf(family.parentUid)).await() }
        }
        return (byList + legacy).distinctBy { it.id }.sortedBy { it.createdAt }
    }

    /**
     * The child profile the parent app is currently showing — every parent-side screen resolves its
     * familyId through this, so switching children ([selectFamily]) switches the whole app. Falls back
     * to the oldest profile when nothing was selected yet or the selection no longer exists.
     */
    suspend fun findFamilyIdForParent(parentUid: String): String? {
        val ids = familiesForParent(parentUid).map { it.id }
        val selected = selectionPrefs.getString(parentUid, null)
        return selected?.takeIf { it in ids } ?: ids.firstOrNull()
    }

    fun selectFamily(parentUid: String, familyId: String) {
        selectionPrefs.edit().putString(parentUid, familyId).apply()
    }

    /**
     * New, still-unpaired profile for another child. Shares the parents, SOS phone, protection PIN
     * and timezone of [templateFamilyId] — those belong to the parents, not to one child — so the
     * second parent sees the new child too and the parent app's PIN lock keeps working unchanged.
     */
    suspend fun createChildProfile(parentUid: String, templateFamilyId: String): String {
        val template = getFamily(templateFamilyId) ?: error("template family missing")
        val doc = familiesRef().document()
        doc.set(
            Family(
                id = doc.id,
                parentUid = parentUid,
                parentUids = (template.allParentUids() + parentUid).distinct(),
                parentEmail = template.parentEmail,
                parentPhone = template.parentPhone,
                timezone = template.timezone,
                protectionPinHash = template.protectionPinHash,
                protectionPinSalt = template.protectionPinSalt
            )
        ).await()
        return doc.id
    }

    /** Only for a profile no child ever claimed — e.g. "add child" cancelled, or the empty profile a
     *  second parent got at sign-up before joining the real family. The rules refuse it otherwise. */
    suspend fun deleteUnpairedProfile(familyId: String) {
        familiesRef().document(familyId).delete().await()
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

    /** Written to every child profile the parent manages — one PIN guards the parent app and all
     *  children's phones, see [createChildProfile]. */
    suspend fun setProtectionPin(parentUid: String, pin: String) {
        val hashed = PinHasher.hash(pin)
        familiesForParent(parentUid).forEach { family ->
            familiesRef().document(family.id)
                .update(
                    mapOf(
                        "protectionPinHash" to hashed.hash,
                        "protectionPinSalt" to hashed.salt
                    )
                ).await()
        }
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

    /** Same number on every child profile — see [setProtectionPin]. */
    suspend fun setParentPhone(parentUid: String, phone: String) {
        familiesForParent(parentUid).forEach { family ->
            familiesRef().document(family.id).update("parentPhone", phone).await()
        }
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
