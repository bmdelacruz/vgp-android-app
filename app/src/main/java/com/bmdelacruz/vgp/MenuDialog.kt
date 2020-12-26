package com.bmdelacruz.vgp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MenuDialog : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_menu, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.item_settings).setOnClickListener {
            requireContext().apply {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            dismiss()
        }
        view.findViewById<TextView>(R.id.item_help).setOnClickListener {
            // TODO

            dismiss()
        }
        view.findViewById<TextView>(R.id.item_about).setOnClickListener {
            // TODO

            dismiss()
        }
    }
}