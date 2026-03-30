package com.example.chess

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.math.min

class ChessView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paintLight = Paint().apply { color = Color.parseColor("#EEEEEE") }
    private val paintDark = Paint().apply { color = Color.parseColor("#779556") }
    private val paintSelected = Paint().apply { color = Color.parseColor("#88FFFF00") }
    private val paintCheck = Paint().apply { color = Color.parseColor("#99FF0000") }

    // New Highlight Paint: Semi-transparent gray dot
    private val paintHighlight = Paint().apply {
        color = Color.parseColor("#44000000")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    var game: ChessGame? = null
    private var cellSide: Float = 0f
    private var fromCol: Int = -1
    private var fromRow: Int = -1
    private var legalMoves: List<Pair<Int, Int>> = emptyList()

    private val pieceDrawables = mutableMapOf<Int, Drawable>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val smaller = min(measuredWidth, measuredHeight)
        setMeasuredDimension(smaller, smaller)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cellSide = width / 8f
        drawBoard(canvas)
        drawHighlights(canvas) // Draw dots under pieces
        drawPieces(canvas)
    }

    private fun drawBoard(canvas: Canvas) {
        for (row in 0..7) {
            for (col in 0..7) {
                // Draw square
                canvas.drawRect(col * cellSide, (7 - row) * cellSide, (col + 1) * cellSide, (7 - row + 1) * cellSide, if ((row + col) % 2 == 0) paintDark else paintLight)

                // Draw selection highlight
                if (col == fromCol && row == fromRow) {
                    canvas.drawRect(col * cellSide, (7 - row) * cellSide, (col + 1) * cellSide, (7 - row + 1) * cellSide, paintSelected)
                }

                // Draw check highlight
                val piece = game?.pieceAt(col, row)
                if (piece?.rank == Rank.KING && game?.isInCheck(piece.player) == true) {
                    canvas.drawRect(col * cellSide, (7 - row) * cellSide, (col + 1) * cellSide, (7 - row + 1) * cellSide, paintCheck)
                }
            }
        }
    }

    private fun drawHighlights(canvas: Canvas) {
        legalMoves.forEach { (c, r) ->
            val centerX = (c + 0.5f) * cellSide
            val centerY = (7 - r + 0.5f) * cellSide

            // If the square is empty, draw a small dot. If occupied, draw a large ring/circle.
            val radius = if (game?.pieceAt(c, r) == null) cellSide / 6f else cellSide / 2.2f

            if (game?.pieceAt(c, r) != null) {
                paintHighlight.style = Paint.Style.STROKE
                paintHighlight.strokeWidth = 6f
            } else {
                paintHighlight.style = Paint.Style.FILL
            }

            canvas.drawCircle(centerX, centerY, radius, paintHighlight)
        }
    }

    private fun drawPieces(canvas: Canvas) {
        game?.piecesBox?.toList()?.forEach { piece ->
            getPieceDrawable(piece)?.let {
                it.setBounds(
                    (piece.col * cellSide).toInt(),
                    ((7 - piece.row) * cellSide).toInt(),
                    ((piece.col + 1) * cellSide).toInt(),
                    ((7 - piece.row + 1) * cellSide).toInt()
                )
                it.draw(canvas)
            }
        }
    }

    private fun getPieceDrawable(piece: Piece): Drawable? {
        val resId = when (piece.player) {
            Player.WHITE -> when (piece.rank) {
                Rank.KING -> R.drawable.ic_white_king; Rank.QUEEN -> R.drawable.ic_white_queen
                Rank.BISHOP -> R.drawable.ic_white_bishop; Rank.KNIGHT -> R.drawable.ic_white_knight
                Rank.ROOK -> R.drawable.ic_white_rook; Rank.PAWN -> R.drawable.ic_white_pawn
            }
            Player.BLACK -> when (piece.rank) {
                Rank.KING -> R.drawable.ic_black_king; Rank.QUEEN -> R.drawable.ic_black_queen
                Rank.BISHOP -> R.drawable.ic_black_bishop; Rank.KNIGHT -> R.drawable.ic_black_knight
                Rank.ROOK -> R.drawable.ic_black_rook; Rank.PAWN -> R.drawable.ic_black_pawn
            }
        }
        return pieceDrawables.getOrPut(resId) { ContextCompat.getDrawable(context, resId)!! }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && game?.isGameOver == false) {
            val col = (event.x / cellSide).toInt()
            val row = 7 - (event.y / cellSide).toInt()

            if (fromCol == -1) {
                val selected = game?.pieceAt(col, row)
                if (selected != null && selected.player == game?.playerTurn) {
                    fromCol = col
                    fromRow = row
                    legalMoves = game?.getLegalMovesForPiece(selected) ?: emptyList()
                }
            } else {
                // This is where movePiece is actually used!
                if (fromCol != col || fromRow != row) {
                    game?.movePiece(fromCol, fromRow, col, row)
                }

                // Reset selection state
                fromCol = -1
                fromRow = -1
                legalMoves = emptyList()

                if (game?.isGameOver == true) {
                    val msg = if (game?.winner != null) "${game?.winner} WINS!" else "STALEMATE!"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
            invalidate() // Refresh UI to show the move and clear highlights
        }
        return true
    }

    fun resetSelection() {
        fromCol = -1
        fromRow = -1
        legalMoves = emptyList()
    }
}