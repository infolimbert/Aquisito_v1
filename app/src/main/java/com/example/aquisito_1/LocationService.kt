package com.example.aquisito_1

// Necesitas estas librerías de soporte V4
// LocationService.kt
import android.content.Intent
import android.view.KeyEvent

import android.support.v4.media.session.MediaSessionCompat // ⭐ MediaSession
import android.support.v4.media.session.PlaybackStateCompat // ⭐ PlaybackState
import android.media.AudioManager


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.session.MediaButtonReceiver
import com.google.android.gms.location.*
import java.util.Locale

private const val TAG = "LocationService"
private const val CHANNEL_ID = "location_service_channel"
private const val NOTIFICATION_ID = 1

// ASUMIDO: Reemplaza o define estas constantes si están en otro archivo
const val ACTION_LOCATION_BROADCAST = "com.example.aquisito_1.LOCATION_UPDATE"
const val EXTRA_ADDRESS = "extra_address"
// --- FIN ASUMIDO ---

class LocationService : Service(), TextToSpeech.OnInitListener {

    // LocationService.kt (Dentro de la clase LocationService)

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionCallback: MediaSessionCompat.Callback
    private var lastStreetMessage: String = "Servicio iniciado. Esperando ubicación."
    private var silencePlayer: SilencePlayer? = null // ⭐ Nuevo

    // Asegúrate de que TAG y los logs estén definidos
    private val TAG = "LocationService"


    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationManager: NotificationManager
    private lateinit var locationRequest: LocationRequest // Asumiendo que la tienes

    // ⭐ VARIABLES DE TTS
    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false

    // ⭐ VARIABLES DE LÓGICA DE CRUCE (MIGRADO DEL FRAGMENT)
    private var currentThoroughfareDisplayed: String? = null
    private var previousThoroughfare: String? = null
    private var isDisplayingIntersection = false
    private var intersectionCounter = 0
    private val intersectionDisplayLimit = 3
    private var lastFeatureName: String? = null

    // bandera pera el mensaje de falla de internet
    private var hasAnnouncedError = false // ⭐ NUEVA BANDERA DE ESTADO

    // ----------------------------------------------------
    // TTS: INICIALIZACIÓN, FUNCIÓN Y CICLO DE VIDA
    // ----------------------------------------------------
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("es", "ES")
            val result = tts.setLanguage(locale)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "El idioma español no está disponible en el servicio.")
            } else {
                isTtsInitialized = true
                Log.d(TAG, "TTS en el servicio inicializado con éxito.")
            }
        } else {
            Log.e(TAG, "Fallo en la inicialización de TTS en el servicio: $status")
        }
    }

    // TTS: Función de lectura
    fun speak(text: String) {
        if (isTtsInitialized && text.isNotEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_address")
            Log.d(TAG, "TTS: Hablando: $text")
        } else if (!isTtsInitialized) {
            Log.w(TAG, "TTS no inicializado. No se puede hablar: $text")
        }
    }

    // ----------------------------------------------------
    // CICLO DE VIDA DEL SERVICIO
    // ----------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ⭐ INICIALIZAR TTS
        tts = TextToSpeech(applicationContext, this)

        createNotificationChannel()

        // ASUMIDO: Inicialización de LocationRequest
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(3000)
            .setMaxUpdateDelayMillis(7000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    onNewLocation(location)
                }
            }
        }
        // ⭐ REGISTRAR RECEPTOR DE GPS
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        registerReceiver(gpsStatusReceiver, filter)

        // ⭐ NUEVA IMPLEMENTACIÓN DE MEDIA SESSION
        initializeMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification().build() // Llamada a .build()
        startForeground(NOTIFICATION_ID, notification)

        //Iniciar la solicitud de actualizaciones de ubicacion
        startLocationUpdates()
        return START_STICKY //El servicio se reiniciará si es terminado por el sistema
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 1. Detener las actualizaciones de ubicación
        stopLocationUpdates()
        stopSelf()
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved: Aplicación eliminada de recientes. Deteniendo servicio.")
    }

    override fun onDestroy() {

        // ⭐ LIBERAR REPRODUCTOR DE SILENCIO
        silencePlayer?.release()
        silencePlayer = null
        Log.d(TAG, "SilencePlayer liberado.")

        // ⭐ LIBERAR MEDIASESSION
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
            Log.d(TAG, "MediaSession liberada.")
        }

        // ⭐ DESREGISTRAR RECEPTOR DE GPS
        unregisterReceiver(gpsStatusReceiver)

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        stopLocationUpdates()
        super.onDestroy()
        Log.d(TAG, "Servicio de ubicación destruido.")
    }


    // LocationService.kt (Dentro de la clase LocationService)

    private val gpsStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // El sistema envía esta señal cuando cualquier proveedor de ubicación cambia (incluido el GPS)
            if (intent?.action == android.location.LocationManager.PROVIDERS_CHANGED_ACTION) {
                checkGpsStatusAndNotify()
            }
        }
    }

    private fun initializeMediaSession() {
        val mediaButtonReceiverComponent = ComponentName(this,VoiceButtonReceiver::class.java)

        mediaSession = MediaSessionCompat(this, TAG, mediaButtonReceiverComponent, null).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true

            // --- 1. CONFIGURACIÓN DEL ESTADO ---
            val playbackState = PlaybackStateCompat.Builder().run {
                setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                // ⭐ IMPORTANTE: Colocamos el estado en PLAYING porque estamos reproduciendo silencio.
                setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
            }.build()
            setPlaybackState(playbackState)

            // Definición del Callback para interceptar los eventos de botón
            mediaSessionCallback = object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (event?.action == KeyEvent.ACTION_DOWN) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY,
                            KeyEvent.KEYCODE_MEDIA_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_HEADSETHOOK -> {
                                Log.d(TAG, "Media Button PRESS: ANUNCIANDO UBICACIÓN")
                                // ⭐ ACCIÓN CLAVE: ANUNCIAR UBICACIÓN
                                announceCurrentLocation()
                                return true // Evento consumido
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            }

            setCallback(mediaSessionCallback)
        }
        // ⭐ --- 3. INICIAR REPRODUCTOR DE SILENCIO PARA AGARRAR EL FOCO ---
        silencePlayer = SilencePlayer(this).also {
            it.playForever() // Reproducimos el audio en silencio continuamente
            Log.d(TAG, "SilencePlayer iniciado para mantener el foco de audio.")
        }
    }

    private fun announceCurrentLocation() {
        if (isTtsInitialized && lastStreetMessage.isNotEmpty()) {
            speak(lastStreetMessage)
            Log.d(TAG, "Media Button PRESS: Anunciando: $lastStreetMessage")
        } else {
            speak("Buscando su ubicación. Espere un momento.")
        }
    }


    // --------------------------------------------------------
// (Asegúrate de que tus funciones auxiliares estén aquí)
// --------------------------------------------------------

    private fun checkGpsStatusAndNotify() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

        if (!isGpsEnabled) {
            val message = "¡ATENCIÓN! El GPS ha sido desactivado. Active su GPS para continuar."
            Log.w(TAG, message)

            // 1. Comunicar el error al Fragment (UI)
            broadcastLocation(message)

            // 2. Actualizar la notificación (para visibilidad en segundo plano)
            updateNotification(message)

            // 3. Opcional: Avisar por voz (si el TTS está inicializado)
            speak(message)

        } else {
            // Si se reestablece, podemos forzar una actualización de ubicación si fuera necesario,
            // pero generalmente el sistema reanuda las llamadas a locationCallback.
            Log.d(TAG, "GPS Re-habilitado. Reanudando rastreo.")
        }
    }

    // ----------------------------------------------------
    // LÓGICA DE UBICACIÓN Y GEOCIFIDACIÓN
    // ----------------------------------------------------

    private fun onNewLocation(location: Location) {
        var fullAddressText: String
        var address: Address? = null

        // ⭐ VERIFICACIÓN DE CONECTIVIDAD
        if (isNetworkAvailable()) {
            // Caso 1: Hay Internet
            hasAnnouncedError = false // ⭐ Resetear el error si hay conexión
            address = getAddressFromLocation(location)
            // Usamos el resultado de la geocodificación o un mensaje de error si falla
            fullAddressText = address?.getAddressLine(0) ?: "No se encontró la dirección."

            // 2. Procesar la dirección y generar el mensaje/voz
            processAddressAndAnnounce(address, fullAddressText)
        } else {
            // ⭐ Anunciar al usuario la falta de conexión
            val noInternetMessage = "No hay conexión a Internet.Verifique por favor"
            Log.w(TAG, noInternetMessage)
            // pero por ahora solo lo mostraremos en el log y la notificación.
            fullAddressText = noInternetMessage
            address = null // Aseguramos que la geocodificación fallida no use datos antiguos.

            // ⭐ Control de Repetición de Voz:
            if (!hasAnnouncedError) {
                speak(noInternetMessage)
                hasAnnouncedError = true // Establecer la bandera
            }

            // Siempre enviamos el mensaje al Fragment/Notificación para actualizar la UI.
            broadcastLocation(fullAddressText)
            updateNotification(fullAddressText)
        }


    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            // Método obsoleto para versiones anteriores a Android M (API 23)
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }

    // ⭐ NUEVA FUNCIÓN: Implementación de Geocodificación Inversa
    private fun processAddressAndAnnounce(address: Address?, fullAddressText: String) {


        val currentStreet = address?.thoroughfare
        val currentPOI = address?.featureName

        var streetMessage = fullAddressText
        var shouldSpeak = false

        if (address == null) {
            // En este caso, solo comunicamos el error de red o de geocodificación.
            broadcastLocation(fullAddressText)
            updateNotification(fullAddressText)
            return // ⭐ SALIMOS de la función si no hay dirección válida
        }

        // ----------------------------------------------------
        // LÓGICA DE CRUCE Y MENSAJE
        // ----------------------------------------------------
        if (currentStreet != null && currentStreet != currentThoroughfareDisplayed) {

            previousThoroughfare = currentThoroughfareDisplayed
            currentThoroughfareDisplayed = currentStreet
            isDisplayingIntersection = true
            intersectionCounter = 0

            shouldSpeak = true

            lastFeatureName = null
        }

        // --- Lógica de Contador de Cruce ---
        if (isDisplayingIntersection) {
            intersectionCounter++
            if (intersectionCounter > intersectionDisplayLimit) {
                isDisplayingIntersection = false
                previousThoroughfare = null
            }
        }

        // --- CONSTRUCCIÓN FINAL DEL streetMessage ---
        val poiText = if (currentPOI != null && currentPOI.matches("^(Plaza|Parque|Iglesia|Hospital|Mercado|Banco|Universidad)\\b.*".toRegex(RegexOption.IGNORE_CASE))) {
            ", Cerca de: $currentPOI"
        } else {
            ""
        }

        streetMessage = when {
            // CASO 1: Cruce Detectado
            currentThoroughfareDisplayed != null && previousThoroughfare != null && isDisplayingIntersection -> {
                "Entre ${currentThoroughfareDisplayed} y ${previousThoroughfare}.$poiText"
            }
            // CASO 2: Calle Simple con POI
            currentThoroughfareDisplayed != null && poiText.isNotEmpty() -> {
                "En ${currentThoroughfareDisplayed}.$poiText"
            }
            // CASO 3: Calle Simple
            currentThoroughfareDisplayed != null -> {
                "En la ${currentThoroughfareDisplayed}."
            }
            // CASO 4: Dirección Completa o respaldo
            else -> fullAddressText
        }

        // ----------------------------------------------------
        // LLAMADA A LA VOZ Y COMUNICACIÓN
        // ----------------------------------------------------
        if (shouldSpeak) {
            speak(streetMessage) // El servicio habla en segundo plano
        }

        // 3. Comunicar el mensaje de texto al Fragmento (UI)
        broadcastLocation(streetMessage)
        lastStreetMessage = streetMessage
    }


    // Método para iniciar las actualizaciones de ubicación
    private fun startLocationUpdates() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            Log.d(TAG, "Iniciando actualizaciones de ubicación")
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun getAddressFromLocation(location: Location): Address? {
        return try {
            val geocoder = Geocoder(this, Locale("es", "ES"))
            geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error en geocodificación: ${e.message}")
            null
        }
    }

    private fun broadcastLocation(message: String) {
        val intent = Intent(ACTION_LOCATION_BROADCAST).apply {
            putExtra(EXTRA_ADDRESS, message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Ubicación en Primer Plano",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): NotificationCompat.Builder {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            // Asegura que al hacer click se vuelva a la actividad existente
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guía por Voz Activa")
            .setContentText("Buscando tu ubicación...")
            .setSmallIcon(R.drawable.ic_notification) // Asegúrate de tener este ícono
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
    }

    private fun updateNotification(message: String) {
        val notification = buildNotification()
            .setContentText(message)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }



}