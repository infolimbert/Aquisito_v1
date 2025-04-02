package com.example.aquisito_1

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.example.aquisito_1.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var mBinding: ActivityMainBinding



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        //titulo del toolbar
        val toolbar: Toolbar = mBinding.toolbar // Obtén la referencia a la Toolbar
        setSupportActionBar(toolbar)  //Establece la Toolbar como la ActionBar

        val bottomNavigationView: BottomNavigationView = mBinding.bottomNav // Obtén la referencia al BottomNavigationView

        // Configurar el listener para el BottomNavigationView
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_location -> {
                    loadFragment(LocationFragment())
                    true
                }
                R.id.action_config -> {
                    loadFragment(ConfigFragment())
                    true
                }

                else -> {false}
            }
        }

        loadFragment(LocationFragment())
        }




        private fun loadFragment(fragment: Fragment) {
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.hostFragment, fragment)
            transaction.addToBackStack(null)
            transaction.commit()
        }


        fun updateToolbarTitle(title: String) {
            supportActionBar?.title = title
        }
    }
