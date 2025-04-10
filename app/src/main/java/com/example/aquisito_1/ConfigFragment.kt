package com.example.aquisito_1
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

    /*override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.updateToolbarTitle(getString(R.string.config_title))
    }*/
}