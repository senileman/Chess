package com.example.chess

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var chessView: ChessView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tvWhiteTimer: TextView
    private lateinit var tvBlackTimer: TextView
    private lateinit var tvPointDiff: TextView

    // Drawer controls
    private lateinit var etBaseScore: TextInputEditText
    private lateinit var sliderPointDiff: Slider
    private lateinit var tvSliderValue: TextView
    private lateinit var switchTimer: SwitchMaterial
    private lateinit var layoutTimerInputs: LinearLayout
    private lateinit var etWhiteMinutes: TextInputEditText
    private lateinit var etBlackMinutes: TextInputEditText

    // ── Game state ───────────────────────────────────────────────────────────
    private val chessGame = ChessGame()
    private val handler = Handler(Looper.getMainLooper())

    // ── Timer runnable ───────────────────────────────────────────────────────
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!chessGame.isTimerEnabled || chessGame.isGameOver) return
            if (chessGame.playerTurn == Player.WHITE) chessGame.whiteTimeMillis -= 1000
            else chessGame.blackTimeMillis -= 1000
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupDrawerListener()
        setupDrawerButtons()
        setupSlider()
        setupTimerToggle()

        GameRepository.loadGame(this, chessGame)
        syncDrawerToGameState()
        updatePointDiffDisplay()
        updateTimerVisibility()

        chessView.invalidate()
    }

    override fun onStart() {
        super.onStart()
        if (chessGame.isTimerEnabled && !chessGame.isGameOver) handler.post(timerRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(timerRunnable)
        GameRepository.saveGame(this, chessGame)
    }

    // ── View binding ──────────────────────────────────────────────────────────
    private fun bindViews() {
        chessView      = findViewById(R.id.chess_view)
        chessView.game = chessGame
        drawerLayout   = findViewById(R.id.drawer_layout)
        tvWhiteTimer   = findViewById(R.id.tvWhiteTimer)
        tvBlackTimer   = findViewById(R.id.tvBlackTimer)
        tvPointDiff    = findViewById(R.id.tvPointDiff)

        etBaseScore      = findViewById(R.id.etBaseScore)
        sliderPointDiff  = findViewById(R.id.sliderPointDiff)
        tvSliderValue    = findViewById(R.id.tvSliderValue)
        switchTimer      = findViewById(R.id.switchTimer)
        layoutTimerInputs = findViewById(R.id.layoutTimerInputs)
        etWhiteMinutes   = findViewById(R.id.etWhiteMinutes)
        etBlackMinutes   = findViewById(R.id.etBlackMinutes)
    }

    // ── Drawer listener: pause timer on open, resume on close ─────────────────
    private fun setupDrawerListener() {
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: View) {
                // Pause the timer while the menu is open
                handler.removeCallbacks(timerRunnable)
            }

            override fun onDrawerClosed(drawerView: View) {
                // Resume the timer when menu closes (if it should be running)
                if (chessGame.isTimerEnabled && !chessGame.isGameOver) {
                    handler.post(timerRunnable)
                }
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) = Unit
            override fun onDrawerStateChanged(newState: Int) = Unit
        })
    }

    // ── Drawer buttons ────────────────────────────────────────────────────────
    private fun setupDrawerButtons() {
        findViewById<ImageButton>(R.id.btn_menu_graphic).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        findViewById<Button>(R.id.btnDrawerRestart).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            // Closing the drawer will resume the timer via onDrawerClosed;
            // restartGame() immediately cancels it again, so order doesn't matter.
            restartGame()
        }

        findViewById<Button>(R.id.btnRandomize).setOnClickListener {
            if (applyRandomizerSettings()) {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
    }

    // ── Slider ────────────────────────────────────────────────────────────────
    private fun setupSlider() {
        sliderPointDiff.addOnChangeListener { _, value, _ ->
            tvSliderValue.text = formatSliderLabel(value.toInt())
        }
        tvSliderValue.text = formatSliderLabel(0)
    }

    /**
     * Negative pd → white gets the bonus.  Positive pd → black gets the bonus.
     *
     * Example: pd = −24  →  "−24  ⬜ White bonus"
     *          pd = +24  →  "+24  ⬛ Black bonus"
     *          pd =   0  →  "0  (even)"
     */
    private fun formatSliderLabel(pd: Int): String = when {
        pd == 0 -> "0  (even)"
        pd < 0  -> "$pd  ⬜ White bonus"
        else    -> "+$pd  ⬛ Black bonus"
    }

    // ── Timer toggle ──────────────────────────────────────────────────────────
    private fun setupTimerToggle() {
        switchTimer.setOnCheckedChangeListener { _, isChecked ->
            layoutTimerInputs.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (!isChecked) {
                // Disable timer immediately
                chessGame.isTimerEnabled = false
                handler.removeCallbacks(timerRunnable)
                updateTimerVisibility()
            }
            // Enabling is applied on the next Restart so the inputs are fully committed.
        }
    }

    // ── Randomizer ────────────────────────────────────────────────────────────
    /**
     * Validates inputs and calls [ChessGame.randomizeBoard].
     * Returns true if successful (so the drawer can be closed).
     */
    private fun applyRandomizerSettings(): Boolean {
        val bss = etBaseScore.text.toString().toIntOrNull()
        if (bss == null || bss < ChessGame.MIN_SCORE || bss > ChessGame.MAX_SCORE) {
            Toast.makeText(this,
                "Base score must be between ${ChessGame.MIN_SCORE} and ${ChessGame.MAX_SCORE}",
                Toast.LENGTH_SHORT).show()
            return false
        }

        val pd = sliderPointDiff.value.toInt()
        // Neither side should exceed MAX_SCORE after applying the bonus.
        val whiteTarget = bss + if (pd < 0) -pd else 0
        val blackTarget = bss + if (pd > 0) pd else 0
        if (whiteTarget > ChessGame.MAX_SCORE || blackTarget > ChessGame.MAX_SCORE) {
            Toast.makeText(this,
                "Combination exceeds max score (${ChessGame.MAX_SCORE}). " +
                "Lower the base score or the point difference.",
                Toast.LENGTH_LONG).show()
            return false
        }

        handler.removeCallbacks(timerRunnable)
        chessGame.baseStartingScore = bss
        chessGame.pointDifference   = pd
        chessGame.randomizeBoard()
        chessView.resetSelection()
        chessView.invalidate()
        updatePointDiffDisplay()

        Toast.makeText(this, "Board randomized!", Toast.LENGTH_SHORT).show()
        return true
    }

    /**
     * Displays the actual point totals outside the drawer.
     *
     * Format:  "⬜ 39  —  63 ⬛"  (or "⬜ 39  —  39 ⬛" for even)
     * Hidden entirely in standard (non-randomized) mode.
     */
    private fun updatePointDiffDisplay() {
        if (!chessGame.isRandomized) {
            tvPointDiff.visibility = View.GONE
            return
        }
        val pd  = chessGame.pointDifference
        val bss = chessGame.baseStartingScore
        val w   = bss + if (pd < 0) -pd else 0
        val b   = bss + if (pd > 0) pd  else 0
        tvPointDiff.text = "⬜ $w  —  $b ⬛"
        tvPointDiff.visibility = View.VISIBLE
    }

    /** Syncs drawer inputs to whatever was restored from disk. */
    private fun syncDrawerToGameState() {
        etBaseScore.setText(chessGame.baseStartingScore.toString())
        sliderPointDiff.value = chessGame.pointDifference.toFloat().coerceIn(-120f, 120f)
        tvSliderValue.text = formatSliderLabel(chessGame.pointDifference)

        switchTimer.isChecked = chessGame.isTimerEnabled
        layoutTimerInputs.visibility = if (chessGame.isTimerEnabled) View.VISIBLE else View.GONE
        etWhiteMinutes.setText((chessGame.whiteTimeMillis / 60000).toString())
        etBlackMinutes.setText((chessGame.blackTimeMillis / 60000).toString())
    }

    // ── Timer helpers ─────────────────────────────────────────────────────────
    private fun updateTimerUI() {
        tvWhiteTimer.text = formatTime(chessGame.whiteTimeMillis)
        tvBlackTimer.text = formatTime(chessGame.blackTimeMillis)
    }

    private fun updateTimerVisibility() {
        val vis = if (chessGame.isTimerEnabled) View.VISIBLE else View.GONE
        tvWhiteTimer.visibility = vis
        tvBlackTimer.visibility = vis
    }

    private fun formatTime(ms: Long): String =
        String.format("%02d:%02d", (ms / 1000) / 60, (ms / 1000) % 60)

    // ── Restart ───────────────────────────────────────────────────────────────
    /**
     * If the timer toggle is on, commits the minute inputs before restarting.
     * Randomized mode re-randomizes; standard mode resets to normal chess.
     */
    private fun restartGame() {
        handler.removeCallbacks(timerRunnable)

        // Commit timer settings from drawer inputs
        if (switchTimer.isChecked) {
            val wMin = etWhiteMinutes.text.toString().toLongOrNull() ?: 10L
            val bMin = etBlackMinutes.text.toString().toLongOrNull() ?: 10L
            chessGame.whiteTimeMillis = wMin * 60_000L
            chessGame.blackTimeMillis = bMin * 60_000L
            chessGame.isTimerEnabled  = true
        } else {
            chessGame.isTimerEnabled = false
        }

        if (chessGame.isRandomized) {
            chessGame.randomizeBoard()
            updatePointDiffDisplay()
        } else {
            chessGame.reset()
            tvPointDiff.visibility = View.GONE
        }

        chessView.resetSelection()
        chessView.invalidate()
        updateTimerVisibility()
        updateTimerUI()

        if (chessGame.isTimerEnabled) handler.post(timerRunnable)
        Toast.makeText(this, "Game Restarted", Toast.LENGTH_SHORT).show()
    }
}
