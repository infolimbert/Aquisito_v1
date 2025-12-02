package com.example.aquisito_1

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

class SilencePlayer(context: Context) {
    private var asset: AssetFileDescriptor? = null
    private var player: MediaPlayer? = null

    init {
        try {
            // Abrir el archivo de silencio desde raw
            asset = context.resources.openRawResourceFd(R.raw.silence)
        } catch (t: Throwable) {
            Log.e("SilencePlayer", "No se puede abrir el recurso", t)
        }
    }

    // Función para reproducir el archivo de silencio una vez
    fun playOnce() {
        play(false)
    }

    // Función para reproducir el archivo de silencio en bucle
    fun playForever() {
        play(true)
    }

    // Liberar recursos del MediaPlayer
    fun release() {
        player?.release()
        player = null
        asset?.close()
        asset = null
    }

    // Configurar y reproducir el archivo de silencio
    private fun play(looping: Boolean) {
        player?.release() // Liberar el reproductor anterior si existe
        asset?.let { descriptor ->
            player = MediaPlayer().also { player ->
                player.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                player.isLooping = looping
                player.setOnPreparedListener {
                    it.start() // Iniciar la reproducción
                }
                player.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                player.prepareAsync() // Preparar el MediaPlayer de forma asíncrona
            }
        }
    }

}