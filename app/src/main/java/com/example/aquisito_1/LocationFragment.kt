package com.example.aquisito_1

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.aquisito_1.databinding.FragmentLocationBinding

private const val LOCATION_PERMISSION_REQUEST_CODE = 123
private const val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 456

class LocationFragment:Fragment() {

    // ⭐ NUEVA BANDERA DE ESTADO
    private var isTrackingActive = false

    private lateinit var requestLocationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestBackgroundLocationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var locationButton: Button
    private lateinit var enableGpsLauncher: ActivityResultLauncher<Intent>

    // ⭐ Propiedades para guardar la última dirección válida
    private var lastAddress: String? = null
    private var lastFeatureName: String? = null
    private var lastThoroughfare: String? = null
    // ⭐ Nueva variable: La calle que se está reportando actualmente
    private var currentThoroughfareDisplayed: String? = null
    // ⭐ Nueva variable: La calle anterior a la actual (será nuestra calle transversal)
    private var previousThoroughfare: String? = null

    // ... (variables de historial existentes)
    private var isDisplayingIntersection = false
    private var intersectionCounter = 0
    private val intersectionDisplayLimit = 3 // Muestra el mensaje de cruce 3 veces (aprox. 9-15 segundos)


    private lateinit var lBinding: FragmentLocationBinding

    // ⭐ 1. INICIALIZAR EL RECEPTOR DE UBICACIÓN
    private lateinit var locationReceiver: BroadcastReceiver


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // --- 2. IMPLEMENTACIÓN DEL RECEPTOR ---
                locationReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == ACTION_LOCATION_BROADCAST) {
                            val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                            val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                            //  LECTURA DE LA DIRECCIÓN
                            val address = intent.getStringExtra(EXTRA_ADDRESS) ?: "Dirección no disponible"
                            // ⭐ Clave: Extraemos los nuevos campos (pueden ser null)

                            val thoroughfare = intent.getStringExtra(EXTRA_THOROUGHFARE)
                            val featureName = intent.getStringExtra(EXTRA_FEATURE_NAME)

                            Log.d("LocationFragment", "RECEPCIÓN: Lat=$latitude, Lon=$longitude, Dir=$address")
                            Log.d("LocationFragment", "Dirección detallada: Street=$thoroughfare, Feat=$featureName")
                            // Llama a la función para actualizar la UI
                            updateLocationDisplay(address, thoroughfare, featureName)
                        }
                    }
                }
                // --- FIN DEL RECEPTOR ---

        requestLocationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted){
                Log.d("LocationFragment", "Permiso de ubicacion en primer plano concedido")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        requestBackgroundLocationPermission()
                    }else{
                        // Si no se necesita permiso en segundo plano (Android < 10), pasamos a verificar el GPS
                        checkAndManageLocationState()
                    }
                }else{
                    Log.d("LocationFragment", "Permiso de ubicacion en primer plano denegado")
                    stopLocationService() // Asegura que el servicio se detenga si se niega el permiso

                }
        }

        requestBackgroundLocationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    Log.d("LocationFragment", "Permiso de ubicacion en segundo plano concedido")
                }else{
                    Log.w("LocationFragment", "Permiso de ubicacion en segundo plano denegado")
                }
                checkAndManageLocationState()
            }

        enableGpsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // El usuario regresó de la configuración de GPS. Ahora, verificamos el estado.
            checkAndManageLocationState()
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        lBinding = FragmentLocationBinding.inflate(inflater,container,false)
        return lBinding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        locationButton = lBinding.btnEnableGps

        // El OnClickListener: La lógica clave para el interruptor
        locationButton.setOnClickListener {
            // 1. Verificar si el servicio ya está corriendo (basado en el texto actual)
            // Ya que el texto se actualiza en start/stop, esta es la condición correcta para el interruptor.

            if (isTrackingActive) {
                // El servicio está corriendo -> Detenerlo
                stopLocationService()
            } else {
                // El servicio no está corriendo -> Iniciar el flujo de comprobación/solicitud
                val isGpsEnabled = (requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER)
                val fineLocationGranted = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                // Verificar si tiene permisos de primer plano (asumiendo que segundo plano se manejará en checkLocationPermissions)
                if (fineLocationGranted && isGpsEnabled) {
                    // Si ya tiene todo, iniciar directamente
                    startLocationService()
                } else {
                    // Si falta algo, iniciar el flujo de solicitud (que manejará GPS y Permisos)
                    checkLocationPermissions()
                }
            }
            // NOTA IMPORTANTE: La función updateButtonState() se llama al final de start/stopLocationService
            // o en onResume, para asegurar que el texto siempre sea correcto.
        }

        // Al iniciar el fragmento, actualizar el estado del botón
        updateButtonState()
    }


    override fun onResume() {
        super.onResume()
        // Cuando el usuario regresa al fragmento, actualizamos el estado del botón
        updateButtonState()
        // ⭐ 3. REGISTRAR el receptor cuando el fragmento esté visible
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            locationReceiver,
            IntentFilter(ACTION_LOCATION_BROADCAST)
        )
    }

    override fun onPause() {
        super.onPause()
        // ⭐ 4. ANULAR REGISTRO del receptor para evitar fugas de memoria y errores
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(locationReceiver)
    }



    // --- Lógica de UI y Servicios ---

    private fun updateLocationDisplay(
                                      fullAddress: String,
                                      thoroughfare: String?,
                                      featureName: String?

    ) {
        // ----------------------------------------------------
        // ⭐PASO 1: Persistencia (Guardar la última dirección válida)
        // ----------------------------------------------------
        val currentStreet = thoroughfare ?: lastThoroughfare
        val currentPOI = featureName ?: lastFeatureName
        val currentAddressText = fullAddress.takeIf {
            !it.contains("Buscando dirección") && !it.contains("No se encontró la dirección")
        } ?: lastAddress

        // ⭐ CLAVE: LÓGICA DE HISTORIAL DEL CRUCE
        if (currentStreet != null && currentStreet != currentThoroughfareDisplayed) {
            // Hubo un cambio de calle. Movemos la calle mostrada anteriormente a 'previousThoroughfare'.
            previousThoroughfare = currentThoroughfareDisplayed
            currentThoroughfareDisplayed = currentStreet // Actualizamos la calle actual
            isDisplayingIntersection = true // Activamos el mensaje de cruce
            intersectionCounter = 0 // Reiniciamos el contador
        }

        // ⭐ CLAVE: CADUCIDAD DEL POI
        // Si cambiamos de calle, el POI de la calle anterior ya no es relevante.
        if (lastFeatureName != null) {
            lastFeatureName = " "
        }

        // Si estamos mostrando un cruce, incrementamos el contador
        if (isDisplayingIntersection) {
            intersectionCounter++

            // Si el contador supera el límite, forzamos la salida del mensaje de cruce.
            if (intersectionCounter > intersectionDisplayLimit) {
                isDisplayingIntersection = false
                previousThoroughfare = null // Borramos la calle anterior para evitar que se use
            }
        }

        // --- Lógica de Mensaje ---

        // 1. Buscamos un POI significativo (no queremos números o letras al azar)
        var poiMessage: String? = ""
        if (currentPOI != null && currentPOI.matches("^(Plaza|Parque|Iglesia|Hospital|Mercado|Banco|Universidad)\\b.*".toRegex(RegexOption.IGNORE_CASE))) {
            poiMessage = ", Cerca de: $currentPOI"
        }


        // 1. Lógica para construir el mensaje de la calle
        val streetMessage = when {

            // ⭐ CASO 1: Cruce Detectado por Historial
            currentStreet != null &&
                    previousThoroughfare != null &&
                    currentStreet != previousThoroughfare &&
                    isDisplayingIntersection -> {

                // "Entre Calle Bolívar y Chile" (la anterior)
                "Entre $currentStreet y $previousThoroughfare.$poiMessage"
            }

            // CASO 2: Calle Simple con POI (No hubo cambio de calle)
            currentStreet != null && poiMessage != null -> {
                "En $currentStreet.$poiMessage"
            }

            // CASO 3: Calle Simple (Sin POI)
            currentStreet != null -> {
                "En la $currentStreet."
            }

            // CASO 4: Último Recurso
            currentAddressText != null -> {
                "Dirección: $currentAddressText"
            }

            else -> {
                "Buscando ubicación..."
            }
        }
            //2. Actualizar el TextView con la nueva informacion
            lBinding.tvLocation.text = streetMessage
        }

    private fun checkLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("LocationFragment", "Permiso de ubicación en primer plano ya concedido")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestBackgroundLocationPermission()
                } else {
                    checkAndManageLocationState()
                }
            }
            shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Log.d("LocationFragment", "Mostrando justificación de permiso de ubicación")
                showRationaleDialog(
                    getString(R.string.permission_location_rationale_title),
                    getString(R.string.permission_location_rationale_message)
                )
            }
            else -> {
                Log.d("LocationFragment", "Solicitando permiso de ubicación directamente")
                requestLocationPermission()
            }
        }
    }


    private fun requestLocationPermission() {
        requestLocationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun requestBackgroundLocationPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocationPermissionLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        }
    }

    // --- Lógica de control de estado y servicio ---

    private fun updateButtonState() {
        if (isTrackingActive) {
            locationButton.text = getString(R.string.stop_location_tracking)
        } else {
            locationButton.text = getString(R.string.start_location_tracking)
            // Cuando el rastreo se detiene, podemos limpiar el TextView
            lBinding.tvLocation.text = "Ubicación inactiva."

        }
    }

    private fun checkAndManageLocationState() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (fineLocationGranted) {
            if (!isGpsEnabled) {
                // El GPS está deshabilitado, redirigimos al usuario a la configuración
                Log.d("LocationFragment", "GPS deshabilitado, solicitando al usuario que lo active")
                enableGpsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } else {
                // Si el GPS está activado, iniciamos el servicio
                startLocationService()
            }
        } else {
            // Permiso de ubicación no concedido
            stopLocationService()
        }
    }

    private fun startLocationService() {
        if (isTrackingActive) return

        Intent(requireContext(), LocationService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(intent)
            } else {
                requireContext().startService(intent)
            }
        }
        isTrackingActive = true // ⭐ ACTUALIZA LA BANDERA
        Log.d("LocationFragment", "Servicio de ubicación iniciado.")
        updateButtonState() // ⭐ ACTUALIZAR AQUÍ: Cambia a "Detener"
    }

    private fun stopLocationService() {
        if (!isTrackingActive) return // Evita detener si ya está inactivo
        Intent(requireContext(), LocationService::class.java).also { intent ->
            requireContext().stopService(intent)
        }
        isTrackingActive = false // ⭐ ACTUALIZA LA BANDERA
        Log.d("LocationFragment", "Servicio de ubicación detenido.")
        updateButtonState() // ⭐ ACTUALIZAR AQUÍ: Cambia a "Iniciar"
    }

    private fun showRationaleDialog(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
                requestLocationPermission() // Solicita el permiso después de que el usuario entiende la razón
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                // Puedes informar al usuario que la funcionalidad de ubicación no estará disponible
                Log.w("UbicacionFragment", "Permiso de ubicación denegado por el usuario después de la justificación")
                stopLocationService()
            }
            .setCancelable(false) // Evita que el diálogo se cierre al tocar fuera
            .show()
    }


}


