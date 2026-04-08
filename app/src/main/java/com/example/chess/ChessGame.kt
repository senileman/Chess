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
    private var enPassantTarget: Piece? = null

    // --- Randomizer state ---
    var isRandomized: Boolean = false
    var baseStartingScore: Int = DEFAULT_BASE_SCORE
    /**
     * pointDifference > 0  →  white receives the bonus  (white gets BSS + pd)
     * pointDifference < 0  →  black receives the bonus  (black gets BSS + |pd|)
     * pointDifference = 0  →  both sides equal at BSS
     *
     * The BASE side always gets exactly BSS. Only the bonus side rises above it.
     */
    var pointDifference: Int = 0

    companion object {
        const val DEFAULT_BASE_SCORE = 39
        const val MIN_SCORE = 15   // all pawns
        const val MAX_SCORE = 135  // all queens
    }

    init { reset() }

    // -------------------------------------------------------------------------
    // Standard reset (non-randomized)
    // -------------------------------------------------------------------------
    fun reset() {
        piecesBox.clear()
        playerTurn = Player.WHITE
        isGameOver = false
        winner = null
        drawReason = null
        enPassantTarget = null
        isRandomized = false
        var id = 0
        for (col in 0..7) {
            piecesBox.add(Piece(id++, Player.WHITE, Rank.PAWN, col, 1))
            piecesBox.add(Piece(id++, Player.BLACK, Rank.PAWN, col, 6))
        }
        val setup = arrayOf(Rank.ROOK, Rank.KNIGHT, Rank.BISHOP, Rank.QUEEN,
                            Rank.KING, Rank.BISHOP, Rank.KNIGHT, Rank.ROOK)
        for (col in 0..7) {
            piecesBox.add(Piece(id++, Player.WHITE, setup[col], col, 0))
            piecesBox.add(Piece(id++, Player.BLACK, setup[col], col, 7))
        }
    }

    // -------------------------------------------------------------------------
    // Randomized reset
    // -------------------------------------------------------------------------
    fun randomizeBoard() {
        piecesBox.clear()
        playerTurn = Player.WHITE
        isGameOver = false
        winner = null
        drawReason = null
        enPassantTarget = null
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
                piecesBox.add(Piece(id++, player, pieces[i], col, backRow, hasMoved = true))
            }
            for (col in 0..7) {
                piecesBox.add(Piece(id++, player, pieces[7 + col], col, pawnRow, hasMoved = true))
            }
            // King always present, never randomized
            piecesBox.add(Piece(id, player, Rank.KING, 4, backRow, hasMoved = false))
        }

        placeArmy(Player.WHITE, whiteTarget, backRow = 0, pawnRow = 1)
        placeArmy(Player.BLACK, blackTarget, backRow = 7, pawnRow = 6)
    }

    /**
     * Generates exactly 15 pieces whose values sum to [target].
     * Uses fold to avoid a mutable accumulator whose final write would be flagged
     * as an assigned-but-never-read value by the Kotlin compiler.
     */
    private fun generatePieces(target: Int): List<Rank> {
        val count = 15
        val validValues = listOf(1, 3, 5, 9)
        return (0 until count).fold(target to mutableListOf<Rank>()) { (remaining, result), i ->
            val slotsLeft = count - i
            val minVal = (remaining - (slotsLeft - 1) * 9).coerceIn(1, 9)
            val maxVal = (remaining - (slotsLeft - 1) * 1).coerceIn(1, 9)
            val chosen = validValues.filter { it in minVal..maxVal }.randomOrNull() ?: minVal
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
        piecesBox.joinToString(";") { "${it.id},${it.col},${it.row},${it.player.name},${it.rank.name},${it.hasMoved}" }

    fun deserializeBoard(data: String) {
        if (data.isEmpty()) return
        piecesBox.clear()
        data.split(";").forEach { entry ->
            val p = entry.split(",")
            if (p.size == 6) {
                piecesBox.add(
                    Piece(p[0].toInt(), Player.valueOf(p[3]), Rank.valueOf(p[4]),
                          p[1].toInt(), p[2].toInt(), hasMoved = p[5].toBoolean())
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

        if (checkEnPassant(movingPiece, toCol, toRow)) {
            piecesBox.removeAll { it.col == toCol && it.row == fromRow }
            executeMove(movingPiece, toCol, toRow); finalizeTurn(movingPiece, false); return
        }

        if (movingPiece.rank == Rank.KING && abs(toCol - fromCol) == 2) {
            val homeRow = if (movingPiece.player == Player.WHITE) 0 else 7
            if (fromRow == homeRow && fromCol == 4 && (toCol == 2 || toCol == 6)) {
                if (canCastle(fromCol, fromRow, toCol)) {
                    executeMove(movingPiece, toCol, toRow)
                    val rCol = if (toCol > fromCol) 7 else 0
                    val rDest = if (toCol > fromCol) 5 else 3
                    pieceAt(rCol, fromRow)?.let { it.col = rDest; it.hasMoved = true }
                    finalizeTurn(movingPiece, false); return
                }
            }
            return
        }

        if (isMoveLegal(fromCol, fromRow, toCol, toRow)) {
            val wasDouble = movingPiece.rank == Rank.PAWN && abs(toRow - fromRow) == 2
            piecesBox.removeAll { it.col == toCol && it.row == toRow && it.id != movingPiece.id }
            executeMove(movingPiece, toCol, toRow)
            if (movingPiece.rank == Rank.PAWN && (movingPiece.row == 0 || movingPiece.row == 7))
                movingPiece.rank = Rank.QUEEN
            finalizeTurn(movingPiece, wasDouble)
        }
    }

    fun getLegalMovesForPiece(p: Piece): List<Pair<Int, Int>> {
        val moves = mutableListOf<Pair<Int, Int>>()
        for (c in 0..7) for (r in 0..7) {
            if (isMoveLegal(p.col, p.row, c, r)) moves.add(c to r)
            else if (p.rank == Rank.PAWN && checkEnPassant(p, c, r)) moves.add(c to r)
            else if (p.rank == Rank.KING && abs(c - p.col) == 2 && r == p.row) {
                val home = if (p.player == Player.WHITE) 0 else 7
                if (p.row == home && p.col == 4 && canCastle(p.col, p.row, c)) moves.add(c to r)
            }
        }
        return moves
    }

    private fun checkEnPassant(p: Piece, toC: Int, toR: Int): Boolean {
        if (p.rank != Rank.PAWN) return false
        val fw = if (p.player == Player.WHITE) 1 else -1
        return toR == p.row + fw && abs(toC - p.col) == 1 &&
               pieceAt(toC, toR) == null &&
               enPassantTarget?.col == toC && enPassantTarget?.row == p.row
    }

    private fun executeMove(p: Piece, toC: Int, toR: Int) { p.col = toC; p.row = toR; p.hasMoved = true }

    private fun finalizeTurn(movedPiece: Piece, wasDouble: Boolean) {
        enPassantTarget = if (wasDouble) movedPiece else null
        playerTurn = if (playerTurn == Player.WHITE) Player.BLACK else Player.WHITE
        checkGameState()
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

    private fun canCastle(fC: Int, fR: Int, tC: Int): Boolean {
        val king = pieceAt(fC, fR) ?: return false
        if (king.hasMoved || isInCheck(king.player)) return false
        val kingSide = tC > fC
        val rook = pieceAt(if (kingSide) 7 else 0, fR)
        if (rook == null || rook.rank != Rank.ROOK || rook.hasMoved) return false
        if ((if (kingSide) 5..6 else 1..3).any { pieceAt(it, fR) != null }) return false
        val opp = if (king.player == Player.WHITE) Player.BLACK else Player.WHITE
        return (if (kingSide) listOf(5, 6) else listOf(2, 3)).none { isSquareAttacked(it, fR, opp) }
    }

    private fun isSquareAttacked(col: Int, row: Int, attacker: Player): Boolean =
        piecesBox.filter { it.player == attacker }.any { p ->
            if (p.rank == Rank.PAWN) {
                val fw = if (p.player == Player.WHITE) 1 else -1
                abs(p.col - col) == 1 && row == p.row + fw
            } else canMove(p.col, p.row, col, row)
        }

    // -------------------------------------------------------------------------
    // Game-ending conditions
    // -------------------------------------------------------------------------
    private fun checkGameState() {
        // Insufficient material is checked first — it takes priority over stalemate
        // in edge cases where both conditions coincide.
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

        // King vs King
        if (whiteNonKing.isEmpty() && blackNonKing.isEmpty()) return true

        val minorRanks = setOf(Rank.BISHOP, Rank.KNIGHT)

        // King + minor piece vs lone King
        if (whiteNonKing.isEmpty() && blackNonKing.size == 1 && blackNonKing[0].rank in minorRanks) return true
        if (blackNonKing.isEmpty() && whiteNonKing.size == 1 && whiteNonKing[0].rank in minorRanks) return true

        // King + Bishop vs King + Bishop — draw only when both bishops share the same square color
        // Square color is determined by (col + row) % 2: 0 = dark, 1 = light
        if (whiteNonKing.size == 1 && blackNonKing.size == 1 &&
            whiteNonKing[0].rank == Rank.BISHOP && blackNonKing[0].rank == Rank.BISHOP) {
            val wSquareColor = (whiteNonKing[0].col + whiteNonKing[0].row) % 2
            val bSquareColor = (blackNonKing[0].col + blackNonKing[0].row) % 2
            if (wSquareColor == bSquareColor) return true
        }

        return false
    }

    // -------------------------------------------------------------------------
    // Movement rules
    // -------------------------------------------------------------------------
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
        if (fC == tC && fR == (if (p.player == Player.WHITE) 1 else 6) &&
            tR == fR + 2 * fw && pieceAt(fC, fR + fw) == null && pieceAt(tC, tR) == null) return true
        if (abs(tC - fC) == 1 && tR == fR + fw && pieceAt(tC, tR) != null) return true
        return false
    }
}
