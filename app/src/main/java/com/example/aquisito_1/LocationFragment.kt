package com.example.aquisito_1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.aquisito_1.databinding.FragmentLocationBinding

class LocationFragment:Fragment() {

    private var fragmentTitle: String = ""
    private lateinit var lBinding: FragmentLocationBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
       lBinding = FragmentLocationBinding.inflate(inflater,container,false)
        return lBinding.root

    }

    /*override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.updateToolbarTitle(getString(R.string.location_title))
    }*/
}