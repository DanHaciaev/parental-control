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

data class AuthUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val isSignedIn: Boolean = false,
    val familyId: String? = null,
    val parentEmail: String = "",
    val childLinked: Boolean = false,
    val pinSet: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository
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
                    val family = familyRepository.getFamily(familyId)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            isSignedIn = true,
                            familyId = familyId,
                            parentEmail = email,
                            childLinked = !family?.childUid.isNullOrEmpty(),
                            pinSet = !family?.protectionPinHash.isNullOrEmpty()
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
