package com.example.chess

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class GameMenuSheet(private val onRestart: () -> Unit) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.menu_bottom_sheet, container, false)

        view.findViewById<Button>(R.id.btnRestart).setOnClickListener {
            onRestart() // Call the function passed from MainActivity
            dismiss()   // Close the menu
        }

        return view
    }
}