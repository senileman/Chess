package com.example.chess

import android.content.Context

object GameRepository {
    fun saveGame(ctx: Context, game: ChessGame) {
        val prefs = ctx.getSharedPreferences("chess_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("board", game.serialize())
            putString("turn", game.playerTurn.name)
            putLong("wTime", game.whiteTimeMillis)
            putLong("bTime", game.blackTimeMillis)
            putBoolean("timerOn", game.isTimerEnabled)
            putBoolean("over", game.isGameOver)
            // Randomizer fields
            putBoolean("randomized", game.isRandomized)
            putInt("baseScore", game.baseStartingScore)
            putInt("pointDiff", game.pointDifference)
            apply()
        }
    }

    fun loadGame(ctx: Context, game: ChessGame) {
        val prefs = ctx.getSharedPreferences("chess_prefs", Context.MODE_PRIVATE)
        val data = prefs.getString("board", "") ?: ""
        if (data.isEmpty()) return

        // Delegate board parsing to ChessGame — single source of truth for deserialization.
        game.deserializeBoard(data)

        game.playerTurn      = Player.valueOf(prefs.getString("turn", "WHITE")!!)
        game.whiteTimeMillis = prefs.getLong("wTime", 600000)
        game.blackTimeMillis = prefs.getLong("bTime", 600000)
        game.isTimerEnabled  = prefs.getBoolean("timerOn", false)
        game.isGameOver      = prefs.getBoolean("over", false)
        // Randomizer fields
        game.isRandomized       = prefs.getBoolean("randomized", false)
        game.baseStartingScore  = prefs.getInt("baseScore", ChessGame.DEFAULT_BASE_SCORE)
        game.pointDifference    = prefs.getInt("pointDiff", 0)
    }
}
