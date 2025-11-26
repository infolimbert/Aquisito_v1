package com.example.aquisito_1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import java.util.Locale
import kotlin.random.Random



// Constante para el Canal de Broadcast
const val ACTION_LOCATION_BROADCAST = "com.example.aquisito_1.action.LOCATION_BROADCAST"
const val EXTRA_LATITUDE = "extra_latitude"
const val EXTRA_LONGITUDE = "extra_longitude"
const val EXTRA_ADDRESS = "extra_address" //NUEVA CONSTANTE PARA LA DIRECCIÓN

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
            .setMaxUpdateDelayMillis(7000)
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

    // ⭐ NUEVA FUNCIÓN: Implementación de Geocodificación Inversa
    private fun getAddressFromLocation (latitude:Double, longitude: Double): String{
        // Usamos Locale.getDefault() para obtener la dirección en el idioma del dispositivo
        val geocoder=Geocoder(this, Locale.getDefault())
        return try {
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude,1)

            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]

            // Intentamos obtener la calle o el nombre del lugar.
            val thoroughfare = address.thoroughfare // nombre de la calle (ej av. camacho)
            val featureName = address.featureName // nombre de una entidad (ej "plaza murillo")
            val subLocality = address.subLocality // Barrio o SubLocalidad
            val locality = address.locality //ciudad/ localidad (ej "La Paz")

            // Construimos la dirección. Priorizamos la calle, luego el featureName.
            val primary = thoroughfare?: featureName?: "ubicacion sin nombre"

            // Añadimos la ciudad y país si están disponibles
            val secondary= locality?: subLocality?: ""

            // Formato de salida: "Calle Principal, Ciudad, País"
            val country = address.countryCode ?: ""

            if (secondary.isNotEmpty()){
                "$primary, $secondary, $country"
            }else{
                "$primary, $country"

            }
            }   else{
                "No se encontro la dirección"
            }

        }catch (e: Exception){
            // Maneja Geocoding API key errors, network errors, etc.
            Log.e("LocationService", "Error de Geocodificación: ${e.message}")
            "Buscando dirección... (Error de red o servicio)"
        }
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
        // Si llegamos aquí, significa que tenemos al menos uno de los permisos (FINE o COARSE).
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

    //Metodo para enviar los datos al Fragmento usando LocalBroadcastManager
    private fun sendLocationBroadcast(latitude: Double, longitude: Double){
        // ⭐ NUEVO: Obtener la dirección antes de enviar
        val addressText = getAddressFromLocation(latitude, longitude)


        val intent = Intent(ACTION_LOCATION_BROADCAST).apply {
            putExtra(EXTRA_LATITUDE, latitude)
            putExtra(EXTRA_LONGITUDE, longitude)
            putExtra(EXTRA_ADDRESS, addressText) // AQUI ENVIAMOS LA DIRECCION
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("LocationService", "Broadcast enviado: Lat=$latitude, Lon=$longitude, Dir=$addressText")
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
            .setPriority(Notification.PRIORITY_LOW)
            .setOngoing(true) // No se puede deslizar para cerrar
            .build()

        startForeground(NOTIFICATION_ID, notification)//ID único para la notificación
    }

    //Metodo para detener las actualizaciones de ubicacion
    private fun stopLocationUpdates(){
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("LocationService", "actualizaciones de ubicacion DETENIDAS...")
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