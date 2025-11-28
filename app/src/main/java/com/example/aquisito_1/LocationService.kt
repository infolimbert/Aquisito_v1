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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import java.io.IOException
import java.util.Locale
import kotlin.random.Random



// Constante para el Canal de Broadcast
const val ACTION_LOCATION_BROADCAST = "com.example.aquisito_1.action.LOCATION_BROADCAST"
const val EXTRA_LATITUDE = "extra_latitude"
const val EXTRA_LONGITUDE = "extra_longitude"
const val EXTRA_ADDRESS = "extra_address" //NUEVA CONSTANTE PARA LA DIRECCIÓN
//CONSTANTES PARA EL CRUCE DE CALLES
const val EXTRA_THOROUGHFARE = "extra_thoroughfare"
const val EXTRA_FEATURE_NAME = "extra_feature_name"

class LocationService: Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
    private val NOTIFICATION_ID = 101 // ID fijo para la notificación del servicio en primer plano

    data class AddressInfo(
        val fullAddress: String,
        val thoroughfare: String? = null,
        val featureName: String? = null
    )


    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Servicio creado.")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 1. configurar la solicitud de ubicacion (LocationRequest)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,5000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(3000)
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

    // Función optimizada para verificar la disponibilidad de la red
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?

        if (connectivityManager == null) return false

        // Para versiones modernas (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNet = connectivityManager.getNetworkCapabilities(network) ?: return false

            return activeNet.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    activeNet.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    activeNet.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            // Para versiones antiguas (aunque Location Services requiere una API más alta)
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }



    // ⭐ NUEVA FUNCIÓN: Implementación de Geocodificación Inversa
    private fun getAddressFromLocation (latitude:Double, longitude: Double): AddressInfo{
        // Usamos Locale.getDefault() para obtener la dirección en el idioma del dispositivo
        val geocoder=Geocoder(this, Locale.getDefault())

        return try {
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude,1)
            // ⭐ CLAVE: Solo pedimos 1 resultado (el más cercano) para evitar referencias lejanas

            if (addresses.isNullOrEmpty()) {
                AddressInfo("No se encontró la dirección")
            } else {
                val address = addresses[0]

                // 1. Extracción de los componentes esenciales del resultado más cercano
                val thoroughfare = address.thoroughfare // Calle principal (ej. "Av. Camacho")
                val featureName =
                    address.featureName // Punto de interés más cercano (ej. "Plaza Murillo" o un número)
                val subLocality = address.subLocality // Barrio o SubLocalidad
                val locality = address.locality // Ciudad/Localidad (ej. "La Paz")
                val country = address.countryCode ?: ""

                // 2. Construcción de la dirección completa (fullAddress)
                val primary = thoroughfare ?: featureName ?: "Ubicación sin nombre"
                val secondary = locality ?: subLocality ?: ""

                val fullAddressText = if (secondary.isNotEmpty()) {
                    "$primary, $secondary, $country"
                } else {
                    "$primary, $country"
                }
                //devolvemos el objeto de datos
                AddressInfo(
                    fullAddress = fullAddressText,
                    thoroughfare = thoroughfare,
                    featureName = featureName
                )
            }
            }catch (e: IOException){
            // ⭐ MANEJO ESPECÍFICO DE ERROR DE RED O GEOCODER NO DISPONIBLE
            Log.e("LocationService", "Error de red/servicio Geocoder: ${e.message}")
            // Devolver un objeto de error específico en caso de fallo de red
            AddressInfo(
                fullAddress = "Error de red en Geocodificación. Intente de nuevo con conexión.",
                thoroughfare = null,
                featureName = null
            )
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
        val addressInfo: AddressInfo

        // 1. Verificar si hay conexión a Internet
        if (isNetworkAvailable()) {
            // ✅ Red disponible: Llamar a la geocodificación
            addressInfo = getAddressFromLocation(latitude, longitude)

        }else {
            // ❌ Sin red: NO llamar a la geocodificación, crear un objeto AddressInfo de error
            Log.w("LocationService", "No hay conexión a Internet. Saltando la geocodificación.")

            addressInfo = AddressInfo(
                fullAddress = "Sin conexión. Se requiere Internet para obtener la dirección.",
                thoroughfare = null,
                featureName = null
            )
        }
        // 2. Enviar el Broadcast con los datos (reales o de error)
        val intent = Intent(ACTION_LOCATION_BROADCAST).apply {
            putExtra(EXTRA_LATITUDE, latitude)
            putExtra(EXTRA_LONGITUDE, longitude)

            // ⭐ Enviamos los tres campos de dirección
            putExtra(EXTRA_ADDRESS, addressInfo.fullAddress) // AQUI ENVIAMOS LA DIRECCION
            putExtra(EXTRA_THOROUGHFARE, addressInfo.thoroughfare)
            putExtra(EXTRA_FEATURE_NAME, addressInfo.featureName)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("LocationService", "Broadcast enviado: Lat=$latitude, Lon=$longitude, Dir=${addressInfo.fullAddress}, Street=${addressInfo.thoroughfare}, Feat=${addressInfo.featureName}") // Nuevo Log
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