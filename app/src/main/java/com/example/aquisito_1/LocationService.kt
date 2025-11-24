package com.example.aquisito_1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlin.random.Random



// Constante para el Canal de Broadcast
const val ACTION_LOCATION_BROADCAST = "com.example.aquisito_1.action.LOCATION_BROADCAST"
const val EXTRA_LATITUDE = "extra_latitude"
const val EXTRA_LONGITUDE = "extra_longitude"

class LocationService: Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
    private val NOTIFICATION_ID = 101 // ID fijo para la notificación del servicio en primer plano

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Servicio creado.")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 1. configurar la solicitud de ubicacion (LocationRequest)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,5000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(2500)
            .setMaxUpdateDelayMillis(10000)
            .build()

        // 2. Definir el callback que manejara las actualizaciones

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val latitude = location.latitude
                    val longitude = location.longitude

                    // ⭐ Log de confirmación de obtención de coordenadas
                    Log.i("LocationService", "ubicacion obtenida: Lat=$latitude, Log=$longitude")

                    //3. Enviar la ubicacion al Fragmento
                    sendLocationBroadcast(latitude, longitude)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "onStartCommand() ejecutado.")
        startForegroundService()

        //Iniciar la solicitud de actualizaciones de ubicacion
        startLocationUpdates()
        return START_STICKY //El servicio se reiniciará si es terminado por el sistema
    }

    // Método para iniciar las actualizaciones de ubicación
    private fun startLocationUpdates() {
        // Doble verificación de permisos (aunque el Fragmento debería haberlos chequeado)
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationService", "Error: Permisos de ubicación no concedidos. Deteniendo servicio.")
            stopSelf()
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper() // Ejecutar el callback en el hilo principal
            ).addOnFailureListener { e ->
                // Este Listener es clave para diagnosticar fallos, como GPS desactivado
                Log.e("LocationService", "FALLO al iniciar updates: ${e.message}", e)
                // Opcional: Mostrar mensaje al usuario o detener el servicio si falla
            }
            Log.d("LocationService", "Solicitando actualizaciones de ubicación...")
        } catch (e: Exception) {
            // Captura errores inesperados, como problemas con Google Play Services
            Log.e("LocationService", "Error inesperado al solicitar ubicación: ${e.message}", e)
            stopSelf()
        }
    }


    //Metodo para detener las actualizaciones de ubicacion
    private fun stopLocationUpdates(){
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("LocationService", "actualizaciones de ubicacion DETENIDAS...")
    }
    //Metodo para enviar los datos al Fragmento usando LocalBroadcastManager
    private fun sendLocationBroadcast(latitude: Double, longitude: Double){
        val intent = Intent(ACTION_LOCATION_BROADCAST).apply {
            putExtra(EXTRA_LATITUDE, latitude)
            putExtra(EXTRA_LONGITUDE, longitude)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("LocationService", "Broadcast enviado: Lat=$latitude, Lon=$longitude")
    }

    private fun startForegroundService(){
        //Crear Canal de notificaion si es android 0 o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Rastreo de Ubicacion",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        //Construir la notifiacion
        val notification = android.app.Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Ubicacion Activa")
            .setContentText("Rastreando la ubicacion actual...")
            .setSmallIcon(R.drawable.ic_notification)// inserta icono en drawable
            .setOngoing(true) // No se puede deslizar para cerrar
            .build()

        startForeground(NOTIFICATION_ID, notification)//ID único para la notificación
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        Log.d("LocationService", "Servicio destruido y actulizaciones detenidas.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


}