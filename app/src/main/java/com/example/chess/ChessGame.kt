package com.example.chess

import kotlin.math.abs

class ChessGame {
    var piecesBox = mutableSetOf<Piece>()
    var playerTurn: Player = Player.WHITE
    var isGameOver: Boolean = false
    var winner: Player? = null
    var drawReason: String? = null
    var isTimerEnabled: Boolean = false
    var whiteTimeMillis: Long = 900000
    var blackTimeMillis: Long = 900000

    // --- Randomizer state ---
    var isRandomized: Boolean = false
    var baseStartingScore: Int = DEFAULT_BASE_SCORE
    /**
     * pointDifference > 0  →  white receives the bonus  (white gets BSS + pd)
     * pointDifference < 0  →  black receives the bonus  (black gets BSS + |pd|)
     * pointDifference = 0  →  both sides equal at BSS
     */
    var pointDifference: Int = 0

    companion object {
        const val DEFAULT_BASE_SCORE = 39
        const val MIN_SCORE = 15   // all pawns
        const val MAX_SCORE = 135  // all queens
    }

    init { randomizeBoard() }

    // -------------------------------------------------------------------------
    // Randomized reset
    // -------------------------------------------------------------------------
    fun randomizeBoard() {
        piecesBox.clear()
        playerTurn = Player.WHITE
        isGameOver = false
        winner = null
        drawReason = null
        isRandomized = true

        val whiteTarget = (baseStartingScore + if (pointDifference > 0) pointDifference else 0)
            .coerceIn(MIN_SCORE, MAX_SCORE)
        val blackTarget = (baseStartingScore + if (pointDifference < 0) -pointDifference else 0)
            .coerceIn(MIN_SCORE, MAX_SCORE)

        var id = 0
        val backCols = (0..7).filter { it != 4 }  // 7 non-king columns

        fun placeArmy(player: Player, target: Int, backRow: Int, pawnRow: Int) {
            val pieces = generatePieces(target).shuffled()
            backCols.forEachIndexed { i, col ->
                piecesBox.add(Piece(id++, player, pieces[i], col, backRow))
            }
            for (col in 0..7) {
                piecesBox.add(Piece(id++, player, pieces[7 + col], col, pawnRow))
            }
            // King always present, never randomized
            piecesBox.add(Piece(id, player, Rank.KING, 4, backRow))
        }

        placeArmy(Player.WHITE, whiteTarget, backRow = 0, pawnRow = 1)
        placeArmy(Player.BLACK, blackTarget, backRow = 7, pawnRow = 6)
    }

    /**
     * Generates exactly 15 pieces whose values sum to [target].
     */
    private fun generatePieces(target: Int): List<Rank> {
        val count = 15
        val validValues = listOf(1, 3, 5, 9)
        // Weights — tweak these to taste:
        //   higher weight = appears more often when valid
        val weights = mapOf(1 to 2, 3 to 5, 5 to 2, 9 to 1)

        return (0 until count).fold(target to mutableListOf<Rank>()) { (remaining, result), i ->
            val slotsLeft = count - i
            val minVal = (remaining - (slotsLeft - 1) * 9).coerceIn(1, 9)
            val maxVal = (remaining - (slotsLeft - 1) * 1).coerceIn(1, 9)

            val pool = validValues
                .filter { it in minVal..maxVal }
                .flatMap { v -> List(weights[v] ?: 1) { v } }

            val chosen = pool.randomOrNull() ?: minVal
            result.add(valueToRank(chosen))
            (remaining - chosen) to result
        }.second
    }
    private fun valueToRank(value: Int): Rank = when (value) {
        1    -> Rank.PAWN
        3    -> if (Math.random() < 0.5) Rank.KNIGHT else Rank.BISHOP
        5    -> Rank.ROOK
        9    -> Rank.QUEEN
        else -> Rank.PAWN
    }

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------
    fun serialize(): String =
        piecesBox.joinToString(";") { "${it.id},${it.col},${it.row},${it.player.name},${it.rank.name}" }

    fun deserializeBoard(data: String) {
        if (data.isEmpty()) return
        piecesBox.clear()
        data.split(";").forEach { entry ->
            val p = entry.split(",")
            if (p.size == 5) {
                piecesBox.add(
                    Piece(p[0].toInt(), Player.valueOf(p[3]), Rank.valueOf(p[4]),
                          p[1].toInt(), p[2].toInt())
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Core game logic
    // -------------------------------------------------------------------------
    fun pieceAt(col: Int, row: Int): Piece? = piecesBox.find { it.col == col && it.row == row }

    fun movePiece(fromCol: Int, fromRow: Int, toCol: Int, toRow: Int) {
        if (isGameOver) return
        val movingPiece = pieceAt(fromCol, fromRow) ?: return
        if (movingPiece.player != playerTurn) return

        if (isMoveLegal(fromCol, fromRow, toCol, toRow)) {
            piecesBox.removeAll { it.col == toCol && it.row == toRow && it.id != movingPiece.id }
            executeMove(movingPiece, toCol, toRow)
            if (movingPiece.rank == Rank.PAWN && (movingPiece.row == 0 || movingPiece.row == 7))
                movingPiece.rank = Rank.QUEEN
            finalizeTurn()
        }
    }

    fun getLegalMovesForPiece(p: Piece): List<Pair<Int, Int>> {
        val moves = mutableListOf<Pair<Int, Int>>()
        for (c in 0..7) for (r in 0..7) {
            if (isMoveLegal(p.col, p.row, c, r)) moves.add(c to r)
        }
        return moves
    }

    private fun executeMove(p: Piece, toC: Int, toR: Int) { p.col = toC; p.row = toR }

    private fun finalizeTurn() {
        playerTurn = if (playerTurn == Player.WHITE) Player.BLACK else Player.WHITE
        checkGameState()
    }

    private fun canMove(fC: Int, fR: Int, tC: Int, tR: Int, ignored: Piece? = null): Boolean {
        val p = pieceAt(fC, fR) ?: return false
        val dC = abs(tC - fC); val dR = abs(tR - fR)
        return when (p.rank) {
            Rank.KING   -> dC <= 1 && dR <= 1
            Rank.ROOK   -> isPathClear(fC, fR, tC, tR, straight = true, ignored)
            Rank.BISHOP -> isPathClear(fC, fR, tC, tR, straight = false, ignored)
            Rank.QUEEN  -> isPathClear(fC, fR, tC, tR, true, ignored) || isPathClear(fC, fR, tC, tR, false, ignored)
            Rank.KNIGHT -> (dC == 1 && dR == 2) || (dC == 2 && dR == 1)
            Rank.PAWN   -> canPawnMove(fC, fR, tC, tR, p)
        }
    }

    private fun isMoveLegal(fC: Int, fR: Int, tC: Int, tR: Int): Boolean {
        val movingPiece = pieceAt(fC, fR) ?: return false
        if (!canMove(fC, fR, tC, tR)) return false
        val targetPiece = pieceAt(tC, tR)
        if (targetPiece?.player == movingPiece.player) return false
        val oC = movingPiece.col; val oR = movingPiece.row
        movingPiece.col = tC; movingPiece.row = tR
        val inCheck = isInCheck(movingPiece.player, targetPiece)
        movingPiece.col = oC; movingPiece.row = oR
        return !inCheck
    }

    fun isInCheck(player: Player, ignored: Piece? = null): Boolean {
        val king = piecesBox.find { it.rank == Rank.KING && it.player == player } ?: return false
        val opp = if (player == Player.WHITE) Player.BLACK else Player.WHITE
        return piecesBox.filter { it.player == opp && it.id != ignored?.id }.any { p ->
            if (p.rank == Rank.PAWN) {
                val fw = if (p.player == Player.WHITE) 1 else -1
                abs(p.col - king.col) == 1 && king.row == p.row + fw
            } else canMove(p.col, p.row, king.col, king.row, ignored)
        }
    }

    private fun isPathClear(fC: Int, fR: Int, tC: Int, tR: Int, straight: Boolean, ignored: Piece? = null): Boolean {
        if (straight && fC != tC && fR != tR) return false
        if (!straight && abs(fC - tC) != abs(fR - tR)) return false
        val cD = if (tC > fC) 1 else if (tC < fC) -1 else 0
        val rD = if (tR > fR) 1 else if (tR < fR) -1 else 0
        var c = fC + cD; var r = fR + rD
        while (c != tC || r != tR) {
            if (pieceAt(c, r)?.id?.let { it != ignored?.id } == true) return false
            c += cD; r += rD
        }
        return true
    }

    private fun canPawnMove(fC: Int, fR: Int, tC: Int, tR: Int, p: Piece): Boolean {
        val fw = if (p.player == Player.WHITE) 1 else -1
        if (fC == tC && tR == fR + fw && pieceAt(tC, tR) == null) return true
        val twoStepRows = if (p.player == Player.WHITE) setOf(0, 1) else setOf(6, 7)
        if (fC == tC && fR in twoStepRows &&
            tR == fR + 2 * fw && pieceAt(fC, fR + fw) == null && pieceAt(tC, tR) == null) return true
        if (abs(tC - fC) == 1 && tR == fR + fw && pieceAt(tC, tR) != null) return true
        return false
    }

    // -------------------------------------------------------------------------
    // Game-ending conditions
    // -------------------------------------------------------------------------
    private fun checkGameState() {
        if (isInsufficientMaterial()) {
            isGameOver = true
            winner = null
            drawReason = "INSUFFICIENT MATERIAL"
            return
        }

        if (piecesBox.filter { it.player == playerTurn }.none { getLegalMovesForPiece(it).isNotEmpty() }) {
            isGameOver = true
            winner = if (isInCheck(playerTurn))
                (if (playerTurn == Player.WHITE) Player.BLACK else Player.WHITE)
            else { drawReason = "STALEMATE"; null }
        }
    }

    /**
     * Returns true when neither side has enough material to force checkmate:
     *
     *  • King vs King
     *  • King + Bishop vs King
     *  • King + Knight vs King
     *  • King + Bishop vs King + Bishop  (bishops on the same square color)
     */
    private fun isInsufficientMaterial(): Boolean {
        val whiteNonKing = piecesBox.filter { it.player == Player.WHITE && it.rank != Rank.KING }
        val blackNonKing = piecesBox.filter { it.player == Player.BLACK && it.rank != Rank.KING }

        if (whiteNonKing.isEmpty() && blackNonKing.isEmpty()) return true

        val minorRanks = setOf(Rank.BISHOP, Rank.KNIGHT)

        if (whiteNonKing.isEmpty() && blackNonKing.size == 1 && blackNonKing[0].rank in minorRanks) return true
        if (blackNonKing.isEmpty() && whiteNonKing.size == 1 && whiteNonKing[0].rank in minorRanks) return true

        if (whiteNonKing.size == 1 && blackNonKing.size == 1 &&
            whiteNonKing[0].rank == Rank.BISHOP && blackNonKing[0].rank == Rank.BISHOP) {
            val wSquareColor = (whiteNonKing[0].col + whiteNonKing[0].row) % 2
            val bSquareColor = (blackNonKing[0].col + blackNonKing[0].row) % 2
            if (wSquareColor == bSquareColor) return true
        }

        return false
    }
}
