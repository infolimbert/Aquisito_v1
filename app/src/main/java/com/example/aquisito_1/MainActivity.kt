package com.example.aquisito_1
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.aquisito_1.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var mBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        //titulo del toolbar
        val toolbar: Toolbar = mBinding.toolbar // Obtén la referencia a la Toolbar
        setSupportActionBar(toolbar)  //Establece la Toolbar como la ActionBar

        mBinding.bottomNav.setupWithNavController(findNavController(R.id.nav_host_fragment))

        //definimos los fragmentos con un nivel superior , Esto le indica al Componente de Navegación.
        //  ..que este destino no tiene una pantalla "padre" en la navegación principal, y por lo tanto, no debería mostrar la flecha de retroceso
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.locationFragment, R.id.configFragment)
        )

        // Configurar la navegación con el NavController
        setupActionBarWithNavController(findNavController(R.id.nav_host_fragment), appBarConfiguration)

    }
}