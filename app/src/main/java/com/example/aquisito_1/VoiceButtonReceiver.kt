package com.example.aquisito_1

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.util.Log
import android.view.KeyEvent
import androidx.media.session.MediaButtonReceiver


class VoiceButtonReceiver: MediaButtonReceiver() {
    private val TAG = "VoiceButtonReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        // ⭐ LOG DE RECEPCIÓN: ESTO DEBE APARECER SI EL SISTEMA LO RECIBE
        if (Intent.ACTION_MEDIA_BUTTON == intent?.action) {
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            Log.d(TAG, "EVENTO RECIBIDO en Receptor. KeyCode: ${event?.keyCode} | Action: ${event?.action}")
        } else {
            Log.d(TAG, "EVENTO RECIBIDO en Receptor. No es MEDIA_BUTTON.")
        }

        super.onReceive(context, intent)
    }
}