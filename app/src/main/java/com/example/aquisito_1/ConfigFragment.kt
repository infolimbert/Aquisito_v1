package com.example.aquisito_1
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.aquisito_1.databinding.FragmentConfigBinding

class ConfigFragment: Fragment() {

    private lateinit var cBinding: FragmentConfigBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        cBinding = FragmentConfigBinding.inflate(inflater, container, false)
        return cBinding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ⭐ Asegúrate de que el binding se inicializó correctamente
        // y que el ID del botón es correcto en el XML.
        cBinding.btnCloseApp.setOnClickListener {
            cerrarAplicacion()
        }
    }

    private fun cerrarAplicacion() {
        // ⭐ 1. DETENER EL SERVICIO DE UBICACIÓN
        // Crea una Intent para detener explícitamente el servicio
        val serviceIntent = Intent(requireContext(), LocationService::class.java)
        requireContext().stopService(serviceIntent)

        // ⭐ 2. CERRAR LA ACTIVIDAD Y LA TAREA
        // Esto asegura que la aplicación se cierre por completo
        requireActivity().finishAndRemoveTask()
    }

}