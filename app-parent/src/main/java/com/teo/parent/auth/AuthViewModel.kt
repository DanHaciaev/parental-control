package com.teo.parent.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.PairingException
import com.teo.core.repository.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.TimeZone
import javax.inject.Inject

data class ChildSummary(
    val familyId: String,
    val name: String,
    val linked: Boolean
)

data class AuthUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val isSignedIn: Boolean = false,
    /** The currently selected child's profile — see FamilyRepository.findFamilyIdForParent. */
    val familyId: String? = null,
    val parentEmail: String = "",
    val childLinked: Boolean = false,
    val pinSet: Boolean = false,
    val children: List<ChildSummary> = emptyList(),
    val parentInviteCode: String? = null,
    val parentInviteError: String? = null,
    val joinLoading: Boolean = false,
    val joinError: String? = null
) {
    /** The selected profile is a not-yet-paired extra child, so the pairing screen can back out of it. */
    val canCancelAddChild: Boolean
        get() = !childLinked && children.any { it.linked && it.familyId != familyId }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val pairingRepository: PairingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        val user = auth.currentUser
        if (user == null) {
            _uiState.update { it.copy(loading = false) }
        } else {
            loadFamilyState(user.uid, user.email.orEmpty())
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
                val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                val user = result.user ?: error("no user after sign-in")
                loadFamilyState(user.uid, user.email.orEmpty())
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, errorMessage = mapAuthError(e)) }
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
                val trimmedEmail = email.trim()
                val result = auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
                val uid = result.user?.uid ?: error("no user after sign-up")
                val familyId = familyRepository.createFamily(uid, trimmedEmail, TimeZone.getDefault().id)
                _uiState.update {
                    it.copy(
                        loading = false,
                        isSignedIn = true,
                        familyId = familyId,
                        parentEmail = trimmedEmail,
                        childLinked = false,
                        pinSet = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, errorMessage = mapAuthError(e)) }
            }
        }
    }

    /** Re-reads family state from Firestore — call after pairing/PIN screens complete a step. */
    fun refreshFamilyState() {
        val user = auth.currentUser ?: return
        loadFamilyState(user.uid, user.email.orEmpty())
    }

    fun selectChild(familyId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (familyId == _uiState.value.familyId) return
        familyRepository.selectFamily(uid, familyId)
        refreshFamilyState()
    }

    /** Creates an empty profile for another child and selects it — the nav host then shows the
     *  pairing code for it, exactly like first-time setup. */
    fun addChild() {
        val user = auth.currentUser ?: return
        val current = _uiState.value.familyId ?: return
        viewModelScope.launch {
            try {
                val newId = familyRepository.createChildProfile(user.uid, current)
                familyRepository.selectFamily(user.uid, newId)
                loadFamilyState(user.uid, user.email.orEmpty())
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Не удалось добавить ребёнка, попробуйте ещё раз") }
            }
        }
    }

    fun cancelAddChild() {
        val user = auth.currentUser ?: return
        val state = _uiState.value
        val unpaired = state.familyId ?: return
        val fallback = state.children.firstOrNull { it.linked && it.familyId != unpaired } ?: return
        viewModelScope.launch {
            runCatching { familyRepository.deleteUnpairedProfile(unpaired) }
            familyRepository.selectFamily(user.uid, fallback.familyId)
            loadFamilyState(user.uid, user.email.orEmpty())
        }
    }

    /** Code for a second parent covering every child this parent manages right now — children
     *  added later are shared automatically, since createChildProfile copies the parent list. */
    fun createParentInvite() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(parentInviteCode = null, parentInviteError = null) }
            try {
                val familyIds = familyRepository.familiesForParent(uid).map { it.id }
                val code = pairingRepository.generateParentInvite(uid, familyIds)
                _uiState.update { it.copy(parentInviteCode = code) }
            } catch (e: Exception) {
                _uiState.update { it.copy(parentInviteError = "Не удалось создать код, попробуйте ещё раз") }
            }
        }
    }

    fun dismissParentInvite() {
        _uiState.update { it.copy(parentInviteCode = null, parentInviteError = null) }
    }

    /**
     * Joins another parent's family by their invite code. The empty profile this account got
     * automatically at sign-up is removed afterwards, so it doesn't linger as a phantom child
     * waiting for pairing.
     */
    fun joinAsParent(code: String) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(joinLoading = true, joinError = null) }
            try {
                val ownBefore = familyRepository.familiesForParent(user.uid)
                val joinedIds = pairingRepository.joinAsParent(code.filter { it.isDigit() }, user.uid)
                ownBefore
                    .filter { it.id !in joinedIds && it.childUid.isNullOrEmpty() && it.allParentUids() == listOf(user.uid) }
                    .forEach { runCatching { familyRepository.deleteUnpairedProfile(it.id) } }
                familyRepository.selectFamily(user.uid, joinedIds.first())
                _uiState.update { it.copy(joinLoading = false) }
                loadFamilyState(user.uid, user.email.orEmpty())
            } catch (e: PairingException) {
                _uiState.update { it.copy(joinLoading = false, joinError = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(joinLoading = false, joinError = "Не удалось присоединиться, проверьте интернет") }
            }
        }
    }

    fun clearJoinError() {
        _uiState.update { it.copy(joinError = null) }
    }

    fun signOut() {
        auth.signOut()
        _uiState.value = AuthUiState(loading = false)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Retries a few times before giving up: right after a cold start, Firestore can briefly
     * reject reads with PERMISSION_DENIED while the persisted auth token is still attaching to
     * the connection — a transient race, not a real permissions problem. Without the retry this
     * crashed the app outright (uncaught FirebaseFirestoreException), which is what happened here.
     */
    private fun loadFamilyState(uid: String, email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            repeat(RETRY_ATTEMPTS) { attempt ->
                try {
                    val familyId = familyRepository.findFamilyIdForParent(uid)
                        ?: familyRepository.createFamily(uid, email, TimeZone.getDefault().id)
                    val families = familyRepository.familiesForParent(uid)
                    val family = families.firstOrNull { it.id == familyId } ?: familyRepository.getFamily(familyId)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            isSignedIn = true,
                            familyId = familyId,
                            parentEmail = email,
                            childLinked = !family?.childUid.isNullOrEmpty(),
                            pinSet = !family?.protectionPinHash.isNullOrEmpty(),
                            children = families.mapIndexed { index, f ->
                                ChildSummary(
                                    familyId = f.id,
                                    name = f.childName?.takeIf { n -> n.isNotBlank() }
                                        ?: f.childDeviceName?.takeIf { n -> n.isNotBlank() }
                                        ?: "Ребёнок ${index + 1}",
                                    linked = !f.childUid.isNullOrEmpty()
                                )
                            }
                        )
                    }
                    return@launch
                } catch (e: Exception) {
                    if (attempt < RETRY_ATTEMPTS - 1) delay(500L * (attempt + 1))
                }
            }
            _uiState.update {
                it.copy(loading = false, errorMessage = "Не удалось загрузить данные. Проверьте интернет и попробуйте войти ещё раз.")
            }
        }
    }

    private companion object {
        const val RETRY_ATTEMPTS = 3
    }

    private fun mapAuthError(e: Exception): String = when (e) {
        is FirebaseAuthInvalidCredentialsException -> "Неверный email или пароль"
        is FirebaseAuthInvalidUserException -> "Такой пользователь не найден"
        is FirebaseAuthUserCollisionException -> "Аккаунт с таким email уже существует — попробуйте войти"
        is FirebaseAuthWeakPasswordException -> "Пароль слишком простой — минимум 6 символов"
        is FirebaseNetworkException -> "Нет соединения с интернетом"
        else -> "Что-то пошло не так, попробуйте ещё раз"
    }
}
