package com.example.chess

enum class Player {
    WHITE, BLACK
}

enum class Rank {
    KING, QUEEN, BISHOP, KNIGHT, ROOK, PAWN
}

data class Piece(
    val id: Int,
    val player: Player,
    var rank: Rank,
    var col: Int,
    var row: Int
)
