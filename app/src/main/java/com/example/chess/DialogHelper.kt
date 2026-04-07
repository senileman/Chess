package com.example.chess

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText

object DialogHelper {
    fun showTimeSettingDialog(context: Context, onTimeSet: (Long, Long) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_set_time, null)
        val whiteInput = view.findViewById<TextInputEditText>(R.id.etWhiteMinutes)
        val blackInput = view.findViewById<TextInputEditText>(R.id.etBlackMinutes)

        AlertDialog.Builder(context)
            .setTitle("Set Game Timers")
            .setView(view)
            .setPositiveButton("Start") { _, _ ->
                val whiteMs = (whiteInput.text.toString().toLongOrNull() ?: 10) * 60000
                val blackMs = (blackInput.text.toString().toLongOrNull() ?: 10) * 60000
                onTimeSet(whiteMs, blackMs)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}