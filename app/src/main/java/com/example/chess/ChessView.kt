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
    // Sharp edges for pixel art feel (isAntiAlias = false)
    private val paintLight = Paint().apply { color = Color.parseColor("#FFFFFF"); isAntiAlias = false }
    private val paintDark = Paint().apply { color = Color.parseColor("#888888"); isAntiAlias = false }
    private val paintSelected = Paint().apply { color = Color.parseColor("#88FFD700"); isAntiAlias = false }
    private val paintCheck = Paint().apply { color = Color.parseColor("#AAFF0000"); isAntiAlias = false }
    private val paintHighlight = Paint().apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.FILL
        isAntiAlias = false
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
        drawHighlights(canvas)
        drawPieces(canvas)
    }

    private fun drawBoard(canvas: Canvas) {
        for (row in 0..7) {
            for (col in 0..7) {
                val rect = RectF(col * cellSide, (7 - row) * cellSide, (col + 1) * cellSide, (7 - row + 1) * cellSide)
                canvas.drawRect(rect, if ((row + col) % 2 == 0) paintDark else paintLight)

                if (col == fromCol && row == fromRow) {
                    canvas.drawRect(rect, paintSelected)
                }

                val piece = game?.pieceAt(col, row)
                if (piece?.rank == Rank.KING && game?.isInCheck(piece.player) == true) {
                    canvas.drawRect(rect, paintCheck)
                }
            }
        }
    }

    private fun drawHighlights(canvas: Canvas) {
        legalMoves.forEach { (c, r) ->
            val centerX = (c + 0.5f) * cellSide
            val centerY = (7 - r + 0.5f) * cellSide
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
        game?.piecesBox?.forEach { piece ->
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
                // Attempt the move through the Game engine
                game?.movePiece(fromCol, fromRow, col, row)

                // Cleanup selection
                fromCol = -1
                fromRow = -1
                legalMoves = emptyList()

                if (game?.isGameOver == true) {
                    val msg = when {
                        game?.winner != null  -> "${game?.winner} WINS!"
                        else                  -> game?.drawReason ?: "DRAW!"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
            invalidate()
        }
        return true
    }

    fun resetSelection() {
        fromCol = -1
        fromRow = -1
        legalMoves = emptyList()
    }
}
