package com.example.aquisito_1

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.aquisito_1.databinding.FragmentLocationBinding

private const val LOCATION_PERMISSION_REQUEST_CODE = 123
private const val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 456

class LocationFragment:Fragment() {

    private lateinit var requestLocationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestBackgroundLocationPermissionLauncher: ActivityResultLauncher<String>


    private lateinit var lBinding: FragmentLocationBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
       lBinding = FragmentLocationBinding.inflate(inflater,container,false)
        return lBinding.root

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestLocationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted){
                Log.d("LocationFragment", "Permiso de ubicacion en primer plano concedido")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        requestBackgroundLocationPermission()
                    }else{
                        starLocationService() //iniciar el servicio aqui en versiones anteriores
                    }
                }else{
                    Log.d("LocationFragment", "Permiso de ubicacion en primer plano denegado")
                    // Informar al usuario sobre la denegación
                }
        }

        requestBackgroundLocationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted){
                        Log.d("LocationFragment", "Permiso de ubicacion en segundo plano concedido")
                        starLocationService() //iniciar el servicio despues de obtener ambos servicios
                }else{
                    Log.w("LocationFragment", "Permiso de ubicacion en segundo plano denegado")
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
            if(ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            ){
                Log.d("LocationFragment", "Permiso de ubicacion en primer plano concedido")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                    requestBackgroundLocationPermission()
                }else{
                    starLocationService()  //Iniciar el servicio si los permisos están OK al inicio
                }
            }else{
                requestLocationPermission()
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

    private fun starLocationService(){
        //inicia el LocationService
        Intent(requireContext(), LocationService::class.java).also { intent ->
            requireActivity().startService(intent)
    }
        Log.d("LocationFragment", "Servicio de ubicacion iniciado")
    }


}