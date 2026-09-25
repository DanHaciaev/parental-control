package com.teo.child.listen

import android.content.Context
import android.content.Intent
import com.teo.core.model.ListenSessionStatus
import com.teo.core.repository.ListenSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches for a parent-initiated "Послушать вокруг" request and hands it to [ListenConsentActivity],
 * which answers automatically (no on-screen prompt) — the microphone itself only ever starts from
 * inside [ListenSessionService]. Same retry-on-error snapshot-listener shape as
 * [com.teo.child.monitor.RuleSyncListener].
 */
@Singleton
class ListenSessionListener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val listenSessionRepository: ListenSessionRepository
) {
    /** Guards against re-launching [ListenConsentActivity] on every unrelated field update the
     *  snapshot listener fires for (e.g. its own future respondedAt write) — only a genuinely new
     *  sessionToken should trigger it again. */
    private var lastPromptedToken: String? = null

    fun start(familyId: String, scope: CoroutineScope) {
        scope.launch {
            while (true) {
                try {
                    listenSessionRepository.observeSession(familyId).collect { session ->
                        if (session == null || session.status != ListenSessionStatus.PENDING_CONSENT) return@collect
                        if (lastPromptedToken == session.sessionToken) return@collect
                        lastPromptedToken = session.sessionToken
                        context.startActivity(
                            Intent(context, ListenConsentActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                putExtra(ListenConsentActivity.EXTRA_FAMILY_ID, familyId)
                                putExtra(ListenConsentActivity.EXTRA_SESSION_TOKEN, session.sessionToken)
                                putExtra(ListenConsentActivity.EXTRA_OFFER_SDP, session.offerSdp)
                            }
                        )
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }
}
