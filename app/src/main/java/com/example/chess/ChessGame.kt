package com.example.chess

import kotlin.math.abs

class ChessGame {
    var piecesBox = mutableSetOf<Piece>()
    var playerTurn: Player = Player.WHITE
    var isGameOver: Boolean = false
    var winner: Player? = null
    private var enPassantTarget: Piece? = null

    init {
        reset()
    }

    fun reset() {
        piecesBox.clear()
        playerTurn = Player.WHITE
        isGameOver = false
        winner = null
        enPassantTarget = null

        var idCounter = 0
        // Pawns
        for (i in 0..7) {
            piecesBox.add(Piece(idCounter++, Player.WHITE, Rank.PAWN, i, 1))
            piecesBox.add(Piece(idCounter++, Player.BLACK, Rank.PAWN, i, 6))
        }
        // Power Pieces
        val setup = arrayOf(Rank.ROOK, Rank.KNIGHT, Rank.BISHOP, Rank.QUEEN, Rank.KING, Rank.BISHOP, Rank.KNIGHT, Rank.ROOK)
        for (i in 0..7) {
            piecesBox.add(Piece(idCounter++, Player.WHITE, setup[i], i, 0))
            piecesBox.add(Piece(idCounter++, Player.BLACK, setup[i], i, 7))
        }
    }

    fun pieceAt(col: Int, row: Int): Piece? = piecesBox.find { it.col == col && it.row == row }

    /**
     * Entry point used by ChessView.onTouchEvent
     */
    fun movePiece(fromCol: Int, fromRow: Int, toCol: Int, toRow: Int) {
        if (isGameOver) return
        val movingPiece = pieceAt(fromCol, fromRow) ?: return
        if (movingPiece.player != playerTurn) return

        // 1. En Passant Execution (Must check first because it moves to an empty square)
        if (checkEnPassant(movingPiece, toCol, toRow)) {
            // Remove the victim pawn (which is on the same row as the start, but new column)
            piecesBox.removeAll { it.col == toCol && it.row == fromRow }
            executeMove(movingPiece, toCol, toRow)
            finalizeTurn(movingPiece, false)
            return
        }

        // 2. Castling Execution
        if (movingPiece.rank == Rank.KING && abs(toCol - fromCol) == 2) {
            if (canCastle(fromCol, fromRow, toCol, toRow)) {
                executeMove(movingPiece, toCol, toRow)
                val rookCol = if (toCol > fromCol) 7 else 0
                val rookDestCol = if (toCol > fromCol) 5 else 3
                pieceAt(rookCol, fromRow)?.let { it.col = rookDestCol; it.hasMoved = true }
                finalizeTurn(movingPiece, false)
            }
            return
        }

        // 3. Standard Move Execution
        if (isMoveLegal(fromCol, fromRow, toCol, toRow)) {
            val wasDoubleStep = movingPiece.rank == Rank.PAWN && abs(toRow - fromRow) == 2

            // Standard Capture
            piecesBox.removeAll { it.col == toCol && it.row == toRow && it.id != movingPiece.id }

            executeMove(movingPiece, toCol, toRow)

            // Auto-Promotion
            if (movingPiece.rank == Rank.PAWN && (movingPiece.row == 0 || movingPiece.row == 7)) {
                movingPiece.rank = Rank.QUEEN
            }

            finalizeTurn(movingPiece, wasDoubleStep)
        }
    }

    /**
     * Used by ChessView to draw move highlights.
     */
    fun getLegalMovesForPiece(p: Piece): List<Pair<Int, Int>> {
        val moves = mutableListOf<Pair<Int, Int>>()
        for (c in 0..7) {
            for (r in 0..7) {
                // Check if move is legal via standard rules OR special rules
                if (isMoveLegal(p.col, p.row, c, r) ||
                    checkEnPassant(p, c, r) ||
                    (p.rank == Rank.KING && abs(c - p.col) == 2 && canCastle(p.col, p.row, c, r))) {
                    moves.add(Pair(c, r))
                }
            }
        }
        return moves
    }

    /**
     * Strict verification for En Passant.
     */
    private fun checkEnPassant(p: Piece, toC: Int, toR: Int): Boolean {
        if (p.rank != Rank.PAWN) return false

        val forwardDir = if (p.player == Player.WHITE) 1 else -1

        return toR == p.row + forwardDir &&       // MUST be moving one row forward
                abs(toC - p.col) == 1 &&          // MUST be exactly one column over
                pieceAt(toC, toR) == null &&      // Target square MUST be empty
                enPassantTarget != null &&
                enPassantTarget?.col == toC &&
                enPassantTarget?.row == p.row     // The target pawn is sitting next to us
    }

    private fun executeMove(p: Piece, toC: Int, toR: Int) {
        p.col = toC
        p.row = toR
        p.hasMoved = true
    }

    private fun finalizeTurn(movedPiece: Piece, wasDoubleStep: Boolean) {
        enPassantTarget = if (wasDoubleStep) movedPiece else null
        playerTurn = if (playerTurn == Player.WHITE) Player.BLACK else Player.WHITE
        checkGameState()
    }

    private fun isMoveLegal(fC: Int, fR: Int, tC: Int, tR: Int): Boolean {
        val movingPiece = pieceAt(fC, fR) ?: return false
        if (!canMove(fC, fR, tC, tR)) return false

        val targetPiece = pieceAt(tC, tR)
        if (targetPiece?.player == movingPiece.player) return false

        // Simulation
        val oldCol = movingPiece.col
        val oldRow = movingPiece.row
        movingPiece.col = tC
        movingPiece.row = tR

        val inCheck = isInCheck(movingPiece.player, targetPiece)

        movingPiece.col = oldCol
        movingPiece.row = oldRow
        return !inCheck
    }

    fun isInCheck(player: Player, ignoredPiece: Piece? = null): Boolean {
        val king = piecesBox.find { it.rank == Rank.KING && it.player == player } ?: return false
        val opponent = if (player == Player.WHITE) Player.BLACK else Player.WHITE

        return piecesBox.filter { it.player == opponent && it.id != ignoredPiece?.id }.any { p ->
            if (p.rank == Rank.PAWN) {
                val fw = if (p.player == Player.WHITE) 1 else -1
                abs(p.col - king.col) == 1 && king.row == p.row + fw
            } else {
                canMove(p.col, p.row, king.col, king.row, ignoredPiece)
            }
        }
    }

    private fun canCastle(fC: Int, fR: Int, tC: Int, tR: Int): Boolean {
        val king = pieceAt(fC, fR) ?: return false
        if (king.hasMoved || isInCheck(king.player)) return false

        val isKingSide = tC > fC
        val rook = pieceAt(if (isKingSide) 7 else 0, fR)
        if (rook == null || rook.rank != Rank.ROOK || rook.hasMoved) return false

        val range = if (isKingSide) (fC + 1)..6 else 1..(fC - 1)
        if (range.any { pieceAt(it, fR) != null }) return false

        val pathCols = if (isKingSide) listOf(5, 6) else listOf(2, 3)
        val opponent = if (king.player == Player.WHITE) Player.BLACK else Player.WHITE
        return pathCols.none { isSquareAttacked(it, fR, opponent) }
    }

    private fun isSquareAttacked(col: Int, row: Int, attackerColor: Player): Boolean {
        return piecesBox.filter { it.player == attackerColor }.any { p ->
            if (p.rank == Rank.PAWN) {
                val fw = if (p.player == Player.WHITE) 1 else -1
                abs(p.col - col) == 1 && row == p.row + fw
            } else {
                canMove(p.col, p.row, col, row)
            }
        }
    }

    private fun checkGameState() {
        val hasMoves = piecesBox.filter { it.player == playerTurn }.any { p ->
            getLegalMovesForPiece(p).isNotEmpty()
        }
        if (!hasMoves) {
            isGameOver = true
            winner = if (isInCheck(playerTurn)) (if (playerTurn == Player.WHITE) Player.BLACK else Player.WHITE) else null
        }
    }

    private fun canMove(fC: Int, fR: Int, tC: Int, tR: Int, ignoredPiece: Piece? = null): Boolean {
        val p = pieceAt(fC, fR) ?: return false
        val dC = abs(tC - fC)
        val dR = abs(tR - fR)
        return when (p.rank) {
            Rank.ROOK -> isPathClear(fC, fR, tC, tR, true, ignoredPiece)
            Rank.BISHOP -> isPathClear(fC, fR, tC, tR, false, ignoredPiece)
            Rank.QUEEN -> isPathClear(fC, fR, tC, tR, true, ignoredPiece) || isPathClear(fC, fR, tC, tR, false, ignoredPiece)
            Rank.KING -> dC <= 1 && dR <= 1
            Rank.KNIGHT -> (dC == 1 && dR == 2) || (dC == 2 && dR == 1)
            Rank.PAWN -> canPawnMove(fC, fR, tC, tR, p)
        }
    }

    private fun isPathClear(fC: Int, fR: Int, tC: Int, tR: Int, straight: Boolean, ignoredPiece: Piece? = null): Boolean {
        if (straight && fC != tC && fR != tR) return false
        if (!straight && abs(fC - tC) != abs(fR - tR)) return false
        val cDir = if (tC > fC) 1 else if (tC < fC) -1 else 0
        val rDir = if (tR > fR) 1 else if (tR < fR) -1 else 0
        var c = fC + cDir; var r = fR + rDir
        while (c != tC || r != tR) {
            val p = pieceAt(c, r)
            if (p != null && p.id != ignoredPiece?.id) return false
            c += cDir; r += rDir
        }
        return true
    }

    private fun canPawnMove(fC: Int, fR: Int, tC: Int, tR: Int, p: Piece): Boolean {
        val fw = if (p.player == Player.WHITE) 1 else -1
        val startRow = if (p.player == Player.WHITE) 1 else 6

        // 1. Forward 1
        if (fC == tC && tR == fR + fw && pieceAt(tC, tR) == null) return true
        // 2. Forward 2
        if (fC == tC && fR == startRow && tR == fR + 2 * fw &&
            pieceAt(fC, fR + fw) == null && pieceAt(tC, tR) == null) return true
        // 3. Capture
        if (abs(tC - fC) == 1 && tR == fR + fw && pieceAt(tC, tR) != null) return true

        return false
    }
}