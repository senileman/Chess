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
            apply()
        }
    }

    fun loadGame(ctx: Context, game: ChessGame) {
        val prefs = ctx.getSharedPreferences("chess_prefs", Context.MODE_PRIVATE)
        val data = prefs.getString("board", "") ?: ""
        if (data.isEmpty()) return

        game.piecesBox.clear()
        data.split(";").forEach {
            val p = it.split(",")
            val piece = Piece(p[0].toInt(), Player.valueOf(p[3]), Rank.valueOf(p[4]), p[1].toInt(), p[2].toInt())
            piece.hasMoved = p[5].toBoolean()
            game.piecesBox.add(piece)
        }
        game.playerTurn = Player.valueOf(prefs.getString("turn", "WHITE")!!)
        game.whiteTimeMillis = prefs.getLong("wTime", 600000)
        game.blackTimeMillis = prefs.getLong("bTime", 600000)
        game.isTimerEnabled = prefs.getBoolean("timerOn", false)
        game.isGameOver = prefs.getBoolean("over", false)
    }
}