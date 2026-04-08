package com.example.chess

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButtonToggleGroup
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
    private lateinit var toggleWhiteTime: MaterialButtonToggleGroup
    private lateinit var toggleBlackTime: MaterialButtonToggleGroup

    // ── Game state ───────────────────────────────────────────────────────────
    private val chessGame = ChessGame()
    private val handler = Handler(Looper.getMainLooper())

    // ── Timer format state ───────────────────────────────────────────────────
    private var whiteTimeSetting: Long = DEFAULT_TIME_MS
    private var blackTimeSetting: Long = DEFAULT_TIME_MS

    // Guard: prevents listeners from firing during programmatic sync
    private var suppressListeners = false

    // ── Time format table ────────────────────────────────────────────────────
    companion object {
        private val TIME_OPTIONS_MS = longArrayOf(
            60 * 60_000L,   // Very Long  – 60 min
            30 * 60_000L,   // Long       – 30 min
            15 * 60_000L,   // Medium     – 15 min
             5 * 60_000L,   // Short      –  5 min
             1 * 60_000L    // Bullet     –  1 min
        )
        private val DEFAULT_TIME_MS = TIME_OPTIONS_MS[2]   // 15 min
        private const val PREF_W_SETTING = "wTimeSetting"
        private const val PREF_B_SETTING = "bTimeSetting"
    }

    private val whiteButtonIds = intArrayOf(
        R.id.btnWhite60, R.id.btnWhite30, R.id.btnWhite15, R.id.btnWhite5, R.id.btnWhite1
    )
    private val blackButtonIds = intArrayOf(
        R.id.btnBlack60, R.id.btnBlack30, R.id.btnBlack15, R.id.btnBlack5, R.id.btnBlack1
    )

    private fun timeMillisFromButtonId(btnId: Int): Long {
        val idx = whiteButtonIds.indexOf(btnId).takeIf { it >= 0 }
            ?: blackButtonIds.indexOf(btnId).takeIf { it >= 0 }
            ?: return DEFAULT_TIME_MS
        return TIME_OPTIONS_MS[idx]
    }

    private fun buttonIdForTime(isWhite: Boolean, timeMs: Long): Int {
        val ids = if (isWhite) whiteButtonIds else blackButtonIds
        val idx = TIME_OPTIONS_MS.indexOfFirst { it == timeMs }.takeIf { it >= 0 } ?: 2
        return ids[idx]
    }

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
        loadTimerSettings()
        setupDrawerListener()
        setupDrawerButtons()
        setupSlider()
        setupTimerToggle()
        setupTimeToggleGroups()

        GameRepository.loadGame(this, chessGame)
        syncDrawerToGameState()
        updatePointDiffDisplay()
        updateTimerVisibility()
        updateTimerUI()

        chessView.invalidate()
    }

    override fun onStart() {
        super.onStart()
        if (chessGame.isTimerEnabled && !chessGame.isGameOver) handler.post(timerRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(timerRunnable)
        saveTimerSettings()
        GameRepository.saveGame(this, chessGame)
    }

    // ── Timer settings persistence ────────────────────────────────────────────
    private fun loadTimerSettings() {
        val prefs = getSharedPreferences("chess_prefs", Context.MODE_PRIVATE)
        whiteTimeSetting = prefs.getLong(PREF_W_SETTING, DEFAULT_TIME_MS)
        blackTimeSetting = prefs.getLong(PREF_B_SETTING, DEFAULT_TIME_MS)
    }

    private fun saveTimerSettings() {
        getSharedPreferences("chess_prefs", Context.MODE_PRIVATE).edit()
            .putLong(PREF_W_SETTING, whiteTimeSetting)
            .putLong(PREF_B_SETTING, blackTimeSetting)
            .apply()
    }

    // ── View binding ──────────────────────────────────────────────────────────
    private fun bindViews() {
        chessView      = findViewById(R.id.chess_view)
        chessView.game = chessGame
        drawerLayout   = findViewById(R.id.drawer_layout)
        tvWhiteTimer   = findViewById(R.id.tvWhiteTimer)
        tvBlackTimer   = findViewById(R.id.tvBlackTimer)
        tvPointDiff    = findViewById(R.id.tvPointDiff)

        etBaseScore       = findViewById(R.id.etBaseScore)
        sliderPointDiff   = findViewById(R.id.sliderPointDiff)
        tvSliderValue     = findViewById(R.id.tvSliderValue)
        switchTimer       = findViewById(R.id.switchTimer)
        layoutTimerInputs = findViewById(R.id.layoutTimerInputs)
        toggleWhiteTime   = findViewById(R.id.toggleWhiteTime)
        toggleBlackTime   = findViewById(R.id.toggleBlackTime)
    }

    // ── Drawer listener ───────────────────────────────────────────────────────
    private fun setupDrawerListener() {
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: View) {
                handler.removeCallbacks(timerRunnable)
            }
            override fun onDrawerClosed(drawerView: View) {
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

        findViewById<android.widget.Button>(R.id.btnRandomize).setOnClickListener {
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
     * Positive pd → white gets the bonus.  Negative pd → black gets the bonus.
     *
     * +24  →  "+24  ⬜ White bonus"
     * −24  →  "−24  ⬛ Black bonus"
     *   0  →  "0  (even)"
     */
    private fun formatSliderLabel(pd: Int): String = when {
        pd == 0 -> "0  (even)"
        pd > 0  -> "+$pd  ⬜ White bonus"
        else    -> "$pd  ⬛ Black bonus"
    }

    // ── Timer toggle ──────────────────────────────────────────────────────────
    private fun setupTimerToggle() {
        switchTimer.setOnCheckedChangeListener { _, isChecked ->
            if (suppressListeners) return@setOnCheckedChangeListener

            layoutTimerInputs.visibility = if (isChecked) View.VISIBLE else View.GONE
            chessGame.isTimerEnabled = isChecked

            handler.removeCallbacks(timerRunnable)
            if (isChecked && !chessGame.isGameOver) {
                handler.post(timerRunnable)
            }
            updateTimerVisibility()
        }
    }

    // ── Time format toggle groups ─────────────────────────────────────────────
    private fun setupTimeToggleGroups() {
        toggleWhiteTime.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (suppressListeners || !isChecked) return@addOnButtonCheckedListener
            whiteTimeSetting = timeMillisFromButtonId(checkedId)
            chessGame.whiteTimeMillis = whiteTimeSetting
            updateTimerUI()
        }

        toggleBlackTime.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (suppressListeners || !isChecked) return@addOnButtonCheckedListener
            blackTimeSetting = timeMillisFromButtonId(checkedId)
            chessGame.blackTimeMillis = blackTimeSetting
            updateTimerUI()
        }
    }

    // ── Randomizer ────────────────────────────────────────────────────────────
    private fun applyRandomizerSettings(): Boolean {
        val bss = etBaseScore.text.toString().toIntOrNull()
        if (bss == null || bss < ChessGame.MIN_SCORE || bss > ChessGame.MAX_SCORE) {
            Toast.makeText(this,
                "Base score must be between ${ChessGame.MIN_SCORE} and ${ChessGame.MAX_SCORE}",
                Toast.LENGTH_SHORT).show()
            return false
        }

        val pd = sliderPointDiff.value.toInt()
        // pd > 0 → white bonus; pd < 0 → black bonus
        val whiteTarget = bss + if (pd > 0) pd else 0
        val blackTarget = bss + if (pd < 0) -pd else 0
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

        chessGame.whiteTimeMillis = whiteTimeSetting
        chessGame.blackTimeMillis = blackTimeSetting

        chessGame.randomizeBoard()
        chessView.resetSelection()
        chessView.invalidate()
        updatePointDiffDisplay()
        updateTimerUI()

        if (chessGame.isTimerEnabled) handler.post(timerRunnable)

        Toast.makeText(this, "Board randomized!", Toast.LENGTH_SHORT).show()
        return true
    }

    /**
     * Displays point totals above the board.
     * pd > 0 → white bonus; pd < 0 → black bonus.
     */
    private fun updatePointDiffDisplay() {
        if (!chessGame.isRandomized) {
            tvPointDiff.visibility = View.GONE
            return
        }
        val pd  = chessGame.pointDifference
        val bss = chessGame.baseStartingScore
        val w   = bss + if (pd > 0) pd  else 0
        val b   = bss + if (pd < 0) -pd else 0
        tvPointDiff.text = "⬜ $w  —  $b ⬛"
        tvPointDiff.visibility = View.VISIBLE
    }

    private fun syncDrawerToGameState() {
        suppressListeners = true

        etBaseScore.setText(chessGame.baseStartingScore.toString())
        sliderPointDiff.value = chessGame.pointDifference.toFloat().coerceIn(-120f, 120f)
        tvSliderValue.text = formatSliderLabel(chessGame.pointDifference)

        switchTimer.isChecked = chessGame.isTimerEnabled
        layoutTimerInputs.visibility = if (chessGame.isTimerEnabled) View.VISIBLE else View.GONE

        toggleWhiteTime.check(buttonIdForTime(isWhite = true,  timeMs = whiteTimeSetting))
        toggleBlackTime.check(buttonIdForTime(isWhite = false, timeMs = blackTimeSetting))

        suppressListeners = false
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
}
