package com.example.chess

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {
    private lateinit var chessView: ChessView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tvWhiteTimer: TextView
    private lateinit var tvBlackTimer: TextView
    private val chessGame = ChessGame()
    private val handler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!chessGame.isTimerEnabled || chessGame.isGameOver) return

            if (chessGame.playerTurn == Player.WHITE) {
                chessGame.whiteTimeMillis -= 1000
            } else {
                chessGame.blackTimeMillis -= 1000
            }

            updateTimerUI()

            if (chessGame.whiteTimeMillis <= 0 || chessGame.blackTimeMillis <= 0) {
                chessGame.isGameOver = true
                val winner = if (chessGame.whiteTimeMillis <= 0) "Black" else "White"
                Toast.makeText(this@MainActivity, "Time out! $winner wins!", Toast.LENGTH_LONG).show()
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Views
        chessView = findViewById(R.id.chess_view)
        chessView.game = chessGame
        drawerLayout = findViewById(R.id.drawer_layout)
        tvWhiteTimer = findViewById(R.id.tvWhiteTimer)
        tvBlackTimer = findViewById(R.id.tvBlackTimer)

        // Navigation
        findViewById<ImageButton>(R.id.btn_menu_graphic).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_restart -> restartGame()
                R.id.nav_set_time -> showTimeSettingDialog()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Load game state from Repository
        GameRepository.loadGame(this, chessGame)
        if (chessGame.isTimerEnabled) {
            tvWhiteTimer.visibility = View.VISIBLE
            tvBlackTimer.visibility = View.VISIBLE
            updateTimerUI()
            if (!chessGame.isGameOver) handler.post(timerRunnable)
        }
        chessView.invalidate()
    }

    override fun onPause() {
        super.onPause()
        // Save using Repository
        GameRepository.saveGame(this, chessGame)
    }

    private fun showTimeSettingDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_set_time, null)
        val whiteInput = view.findViewById<TextInputEditText>(R.id.etWhiteMinutes)
        val blackInput = view.findViewById<TextInputEditText>(R.id.etBlackMinutes)

        AlertDialog.Builder(this)
            .setTitle("Set Game Timers")
            .setView(view)
            .setPositiveButton("Start") { _, _ ->
                chessGame.whiteTimeMillis = (whiteInput.text.toString().toLongOrNull() ?: 10) * 60000
                chessGame.blackTimeMillis = (blackInput.text.toString().toLongOrNull() ?: 10) * 60000
                chessGame.isTimerEnabled = true
                tvWhiteTimer.visibility = View.VISIBLE
                tvBlackTimer.visibility = View.VISIBLE
                updateTimerUI()
                handler.removeCallbacks(timerRunnable)
                handler.post(timerRunnable)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateTimerUI() {
        tvWhiteTimer.text = formatTime(chessGame.whiteTimeMillis)
        tvBlackTimer.text = formatTime(chessGame.blackTimeMillis)
    }

    private fun formatTime(ms: Long): String =
        String.format("%02d:%02d", (ms / 1000) / 60, (ms / 1000) % 60)

    private fun restartGame() {
        handler.removeCallbacks(timerRunnable)
        chessGame.reset()
        chessView.resetSelection()
        chessView.invalidate()
        if (chessGame.isTimerEnabled) handler.post(timerRunnable)
        Toast.makeText(this, "Game Restarted", Toast.LENGTH_SHORT).show()
    }
}