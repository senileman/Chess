package com.example.chess

import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var chessView: ChessView
    private lateinit var drawerLayout: DrawerLayout
    private val chessGame = ChessGame()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chessView = findViewById(R.id.chess_view)
        chessView.game = chessGame
        drawerLayout = findViewById(R.id.drawer_layout)

        val btnMenu = findViewById<ImageButton>(R.id.btn_menu_graphic)
        btnMenu.setOnClickListener {
            // Open the drawer from the left (start)
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_restart -> {
                    restartGame()
                }
            }
            // Close drawer after selection
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun restartGame() {
        chessGame.reset()
        chessView.resetSelection()
        chessView.invalidate()
        Toast.makeText(this, "Game Restarted", Toast.LENGTH_SHORT).show()
    }
}