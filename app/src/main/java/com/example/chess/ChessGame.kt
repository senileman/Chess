package com.example.chess

import kotlin.math.abs

class ChessGame {
    var piecesBox = mutableSetOf<Piece>()
    var playerTurn: Player = Player.WHITE
    var isGameOver: Boolean = false
    var winner: Player? = null
    var isTimerEnabled: Boolean = false
    var whiteTimeMillis: Long = 600000
    var blackTimeMillis: Long = 600000
    private var enPassantTarget: Piece? = null

    init { reset() }

    fun reset() {
        piecesBox.clear(); playerTurn = Player.WHITE; isGameOver = false; winner = null; enPassantTarget = null
        var idCounter = 0
        for (i in 0..7) {
            piecesBox.add(Piece(idCounter++, Player.WHITE, Rank.PAWN, i, 1))
            piecesBox.add(Piece(idCounter++, Player.BLACK, Rank.PAWN, i, 6))
        }
        val setup = arrayOf(Rank.ROOK, Rank.KNIGHT, Rank.BISHOP, Rank.QUEEN, Rank.KING, Rank.BISHOP, Rank.KNIGHT, Rank.ROOK)
        for (i in 0..7) {
            piecesBox.add(Piece(idCounter++, Player.WHITE, setup[i], i, 0))
            piecesBox.add(Piece(idCounter++, Player.BLACK, setup[i], i, 7))
        }
    }

    fun serialize(): String = piecesBox.joinToString(";") { "${it.id},${it.col},${it.row},${it.player.name},${it.rank.name},${it.hasMoved}" }

    fun deserializeBoard(data: String) {
        if (data.isEmpty()) return
        piecesBox.clear()
        data.split(";").forEach {
            val p = it.split(",")
            if (p.size == 6) {
                val piece = Piece(p[0].toInt(), Player.valueOf(p[3]), Rank.valueOf(p[4]), p[1].toInt(), p[2].toInt())
                piece.hasMoved = p[5].toBoolean(); piecesBox.add(piece)
            }
        }
    }

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
                if (canCastle(fromCol, fromRow, toCol, toRow)) {
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
            if (movingPiece.rank == Rank.PAWN && (movingPiece.row == 0 || movingPiece.row == 7)) movingPiece.rank = Rank.QUEEN
            finalizeTurn(movingPiece, wasDouble)
        }
    }

    fun getLegalMovesForPiece(p: Piece): List<Pair<Int, Int>> {
        val moves = mutableListOf<Pair<Int, Int>>()
        for (c in 0..7) for (r in 0..7) {
            if (isMoveLegal(p.col, p.row, c, r)) moves.add(Pair(c, r))
            else if (p.rank == Rank.PAWN && checkEnPassant(p, c, r)) moves.add(Pair(c, r))
            else if (p.rank == Rank.KING && abs(c - p.col) == 2 && r == p.row) {
                val home = if (p.player == Player.WHITE) 0 else 7
                if (p.row == home && p.col == 4 && canCastle(p.col, p.row, c, r)) moves.add(Pair(c, r))
            }
        }
        return moves
    }

    private fun checkEnPassant(p: Piece, toC: Int, toR: Int): Boolean {
        if (p.rank != Rank.PAWN) return false
        val fw = if (p.player == Player.WHITE) 1 else -1
        return toR == p.row + fw && abs(toC - p.col) == 1 && pieceAt(toC, toR) == null && enPassantTarget?.col == toC && enPassantTarget?.row == p.row
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

    private fun canCastle(fC: Int, fR: Int, tC: Int, tR: Int): Boolean {
        val king = pieceAt(fC, fR) ?: return false
        if (king.hasMoved || isInCheck(king.player)) return false
        val isSide = tC > fC
        val rook = pieceAt(if (isSide) 7 else 0, fR)
        if (rook == null || rook.rank != Rank.ROOK || rook.hasMoved) return false
        if ((if (isSide) 5..6 else 1..3).any { pieceAt(it, fR) != null }) return false
        val opp = if (king.player == Player.WHITE) Player.BLACK else Player.WHITE
        return (if (isSide) listOf(5, 6) else listOf(2, 3)).none { isSquareAttacked(it, fR, opp) }
    }

    private fun isSquareAttacked(col: Int, row: Int, attacker: Player): Boolean {
        return piecesBox.filter { it.player == attacker }.any { p ->
            if (p.rank == Rank.PAWN) {
                val fw = if (p.player == Player.WHITE) 1 else -1
                abs(p.col - col) == 1 && row == p.row + fw
            } else canMove(p.col, p.row, col, row)
        }
    }

    private fun checkGameState() {
        if (piecesBox.filter { it.player == playerTurn }.none { getLegalMovesForPiece(it).isNotEmpty() }) {
            isGameOver = true; winner = if (isInCheck(playerTurn)) (if (playerTurn == Player.WHITE) Player.BLACK else Player.WHITE) else null
        }
    }

    private fun canMove(fC: Int, fR: Int, tC: Int, tR: Int, ignored: Piece? = null): Boolean {
        val p = pieceAt(fC, fR) ?: return false
        val dC = abs(tC - fC); val dR = abs(tR - fR)
        return when (p.rank) {
            Rank.KING -> dC <= 1 && dR <= 1
            Rank.ROOK -> isPathClear(fC, fR, tC, tR, true, ignored)
            Rank.BISHOP -> isPathClear(fC, fR, tC, tR, false, ignored)
            Rank.QUEEN -> isPathClear(fC, fR, tC, tR, true, ignored) || isPathClear(fC, fR, tC, tR, false, ignored)
            Rank.KNIGHT -> (dC == 1 && dR == 2) || (dC == 2 && dR == 1)
            Rank.PAWN -> canPawnMove(fC, fR, tC, tR, p)
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
        if (fC == tC && fR == (if (p.player == Player.WHITE) 1 else 6) && tR == fR + 2 * fw && pieceAt(fC, fR + fw) == null && pieceAt(tC, tR) == null) return true
        if (abs(tC - fC) == 1 && tR == fR + fw && pieceAt(tC, tR) != null) return true
        return false
    }
}