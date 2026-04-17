package com.example.nqueenspuzzle

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.nqueenspuzzle.databinding.ActivityGameBinding
import kotlin.concurrent.thread
import kotlin.math.min

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding

    private var N: Int = 8                       // board size (set from intent)
    private lateinit var cols: IntArray          // cols[row] = column index of queen in that row OR -1
    private val cellViews = mutableListOf<TextView>()

    // placement history for undo (if needed)
    private val placementStack = mutableListOf<Int>()

    // timer
    private val handler = Handler(Looper.getMainLooper())
    private var startTimeMillis: Long = 0L
    private var timerRunning = false
    private var pausedElapsedSec: Long = 0L

    // board sizing control: fraction of smaller axis used for the board
    private var boardScale = 0.80f

    // stored sizes so we can re-apply them and avoid re-measure shrink
    private var lastBoardPx = 0
    private var lastCellSize = 0

    // progress dialog replacement
    private var progressDialog: AlertDialog? = null

    companion object {
        const val EXTRA_N = "boardSize"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        N = intent.getIntExtra(EXTRA_N, 8).coerceAtLeast(2)
        cols = IntArray(N) { -1 }

        binding.tvCount.text = "Queens: 0"
        binding.tvTimer.text = "Time: 0s"

        // top buttons (adjust IDs if your layout differs)
        binding.btnReset.setOnClickListener { resetBoard() }
        binding.btnHint.setOnClickListener { showHint() }
        binding.btnSolve.setOnClickListener { startSolveWithBacktracking() }

        // Build board after layout ready (root.post ensures layout measured)
        binding.root.post {
            setupBoard()
            startTimer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        handler.removeCallbacksAndMessages(null)
    }

    // ------------------------ Timer ------------------------
    private val timerTick = object : Runnable {
        override fun run() {
            if (!timerRunning) return
            val elapsedSinceStart = ((System.currentTimeMillis() - startTimeMillis) / 1000)
            val total = pausedElapsedSec + elapsedSinceStart
            binding.tvTimer.text = "Time: ${total}s"
            handler.postDelayed(this, 1000)
        }
    }

    private fun startTimer() {
        if (!timerRunning) {
            startTimeMillis = System.currentTimeMillis()
            timerRunning = true
            handler.post(timerTick)
        }
    }

    private fun stopTimer() {
        if (!timerRunning) return
        val elapsedSinceStart = ((System.currentTimeMillis() - startTimeMillis) / 1000)
        pausedElapsedSec += elapsedSinceStart
        timerRunning = false
        handler.removeCallbacks(timerTick)
    }

    // ------------------------ Board Setup ------------------------
    private fun setupBoard() {
        cellViews.clear()
        binding.gridBoard.removeAllViews()

        // set counts
        binding.gridBoard.columnCount = N
        binding.gridBoard.rowCount = N

        // compute cell size and boardPx once (use min dimension)
        val availableWidth = binding.root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val availableHeight = binding.root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val maxBoardPx = (min(availableWidth, availableHeight) * boardScale).toInt()
        val cellSize = if (N > 0) maxBoardPx / N else 0
        val boardPx = cellSize * N

        // store sizes so we can re-apply them later
        lastBoardPx = boardPx
        lastCellSize = cellSize

        // ensure grid has fixed exact layout size
        val lp = binding.gridBoard.layoutParams
        lp.width = boardPx
        lp.height = boardPx
        binding.gridBoard.layoutParams = lp
        binding.gridBoard.minimumWidth = boardPx
        binding.gridBoard.minimumHeight = boardPx
        binding.gridBoard.clipToPadding = false

        // create cells with fixed width/height (no weights)
        for (r in 0 until N) {
            for (c in 0 until N) {
                val cell = createCellView(r, c, cellSize)
                cellViews.add(cell)
                binding.gridBoard.addView(cell)
            }
        }

        updateCountDisplay()
    }

    private fun createCellView(row: Int, col: Int, cellSize: Int): TextView {
        val tv = TextView(this)

        // Use GridLayout.LayoutParams with fixed width/height — avoids weight-based reflow
        val params = GridLayout.LayoutParams(
            GridLayout.spec(row, 1),
            GridLayout.spec(col, 1)
        ).apply {
            width = cellSize
            height = cellSize
            setMargins(0, 0, 0, 0)
        }

        tv.layoutParams = params
        tv.gravity = Gravity.CENTER

        // text size scaled by board size so it doesn't blow up
        val baseTextSp = when {
            N <= 8 -> 24f
            N <= 16 -> 16f
            else -> 12f
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseTextSp)

        val isLight = (row + col) % 2 == 0
        val lightColor = 0xFFFAE9C8.toInt()
        val darkColor = 0xFF7F5F4F.toInt()
        tv.setBackgroundColor(if (isLight) lightColor else darkColor)
        tv.setTextColor(Color.BLACK)
        tv.text = ""

        // remove default pressed overlays/ripples that can change sizing
        tv.isClickable = true
        tv.isFocusable = false
        tv.foreground = null

        // intercept touch to avoid ripple/pressed visual that may shift layout,
        // but return false so click still works.
        tv.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // prevent Android from setting 'pressed' visual on the view
                v.isPressed = false
            }
            false
        }

        tv.setOnClickListener {
            onCellClicked(row, col, tv)
        }

        return tv
    }

    // ------------------------ Click / Place logic ------------------------
    private fun onCellClicked(row: Int, col: Int, view: TextView) {
        if (cols[row] == col) {
            // remove queen
            removeQueen(row)
            view.text = ""
            if (placementStack.isNotEmpty() && placementStack.last() == row) {
                placementStack.removeAt(placementStack.size - 1)
            }
        } else {
            if (isSafe(row, col)) {
                // remove previous in same row if any
                if (cols[row] != -1) {
                    val prevIdx = row * N + cols[row]
                    if (prevIdx in 0 until cellViews.size) {
                        cellViews[prevIdx].text = ""
                    }
                }
                placeQueen(row, col)
                view.text = "♛"
                placementStack.add(row)
                if (cols.count { it != -1 } == N) {
                    onWin()
                }
            } else {
                // brief red flash then restore cell color
                view.setBackgroundColor(0xFFFF6666.toInt())
                handler.postDelayed({
                    val isLight = (row + col) % 2 == 0
                    val lightColor = 0xFFFAE9C8.toInt()
                    val darkColor = 0xFF7F5F4F.toInt()
                    view.setBackgroundColor(if (isLight) lightColor else darkColor)
                }, 250)
            }
        }
        updateCountDisplay()
    }

    private fun placeQueen(row: Int, col: Int) {
        cols[row] = col
    }

    private fun removeQueen(row: Int) {
        val c = cols[row]
        if (c != -1) {
            val idx = row * N + c
            if (idx in 0 until cellViews.size) {
                cellViews[idx].text = ""
            }
            cols[row] = -1
        }
    }

    private fun updateCountDisplay() {
        val placed = cols.count { it != -1 }
        binding.tvCount.text = "Queens: $placed"
    }

    // ------------------------ Win dialog ------------------------
    private fun onWin() {
        stopTimer()
        AlertDialog.Builder(this)
            .setTitle("You won!")
            .setMessage("All $N queens placed without attacking each other.\nTime: ${binding.tvTimer.text}")
            .setPositiveButton("Play again") { _, _ ->
                resetBoard()
            }
            .setNegativeButton("Exit") { _, _ ->
                // Return to the welcome/home activity
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }
            .show()
    }

    // ------------------------ Safety check ------------------------
    private fun isSafe(row: Int, col: Int): Boolean {
        for (r in 0 until N) {
            val c = cols[r]
            if (c == -1) continue
            if (c == col) return false
            val dr = kotlin.math.abs(r - row)
            val dc = kotlin.math.abs(c - col)
            if (dr == dc) return false
        }
        return true
    }

    // ------------------------ Hint (greedy) ------------------------
    private fun showHint() {
        for (r in 0 until N) {
            if (cols[r] == -1) {
                for (c in 0 until N) {
                    if (isSafe(r, c)) {
                        highlightHint(r, c)
                        return
                    }
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("No hint")
            .setMessage("No obvious single-step hint available. Try moving a queen.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun highlightHint(row: Int, col: Int) {
        val idx = row * N + col
        if (idx !in 0 until cellViews.size) return
        val view = cellViews[idx]
        val originalAlpha = view.alpha
        view.alpha = 0.5f
        view.postDelayed({ view.alpha = originalAlpha }, 600)
    }

    // ------------------------ Backtracking solver ------------------------
    private fun startSolveWithBacktracking() {
        // quick impossible cases
        if (N == 2 || N == 3) {
            AlertDialog.Builder(this)
                .setTitle("No solution")
                .setMessage("There is no solution for N = $N.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val maxMillis = if (N <= 14) 60_000L else 10_000L

        // show modern progress dialog (AlertDialog with ProgressBar)
        val pb = ProgressBar(this).apply {
            isIndeterminate = true
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        progressDialog = AlertDialog.Builder(this)
            .setTitle("Solving")
            .setMessage("Solving for N = $N …")
            .setView(pb)
            .setCancelable(false)
            .create()
        progressDialog?.show()

        // disable UI while solving
        setUiEnabled(false)

        // clear current placement
        for (i in 0 until N) cols[i] = -1
        placementStack.clear()
        updateBoardViewsFromCols()
        updateCountDisplay()

        thread {
            var found = false
            val start = System.currentTimeMillis()

            fun backtrack(row: Int): Boolean {
                if (System.currentTimeMillis() - start > maxMillis) return false
                if (row == N) return true
                for (c in 0 until N) {
                    if (isSafe(row, c)) {
                        cols[row] = c
                        if (backtrack(row + 1)) return true
                        cols[row] = -1
                    }
                }
                return false
            }

            found = backtrack(0)

            runOnUiThread {
                // dismiss modern progress dialog safely
                try { progressDialog?.dismiss() } catch (_: Exception) {}
                progressDialog = null

                setUiEnabled(true)

                // re-apply the stored board size before updating children,
                // this prevents GridLayout from re-measuring smaller/squashed sizes.
                if (lastBoardPx > 0) {
                    val lp = binding.gridBoard.layoutParams
                    lp.width = lastBoardPx
                    lp.height = lastBoardPx
                    binding.gridBoard.layoutParams = lp
                    binding.gridBoard.minimumWidth = lastBoardPx
                    binding.gridBoard.minimumHeight = lastBoardPx
                }

                updateBoardViewsFromCols()
                updateCountDisplay()

                // force re-layout/invalidate so visual is consistent
                binding.gridBoard.requestLayout()
                binding.gridBoard.invalidate()

                if (found) {
                    onWin()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("No solution (backtracking)")
                        .setMessage("Solver didn't find a complete solution within time limit for N = $N.\nTry smaller N or manual play.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        binding.btnReset.isEnabled = enabled
        binding.btnHint.isEnabled = enabled
        binding.btnSolve.isEnabled = enabled
    }

    private fun updateBoardViewsFromCols() {
        // Re-assert grid size again to be safe (in case something changed)
        if (lastBoardPx > 0) {
            val lp = binding.gridBoard.layoutParams
            lp.width = lastBoardPx
            lp.height = lastBoardPx
            binding.gridBoard.layoutParams = lp
            binding.gridBoard.minimumWidth = lastBoardPx
            binding.gridBoard.minimumHeight = lastBoardPx
        }

        // Clear all text then set queens where cols[r] != -1
        for (v in cellViews) v.text = ""
        for (r in 0 until N) {
            val c = cols[r]
            if (c != -1) {
                val idx = r * N + c
                if (idx in 0 until cellViews.size) {
                    val tv = cellViews[idx]
                    tv.text = "♛"
                    val textSp = when {
                        N <= 8 -> 24f
                        N <= 16 -> 18f
                        else -> 12f
                    }
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
                }
            }
        }
    }

    // ------------------------ Reset ------------------------
    private fun resetBoard() {
        for (r in 0 until N) {
            cols[r] = -1
        }
        placementStack.clear()
        for (v in cellViews) v.text = ""
        updateCountDisplay()
        stopTimer()
        pausedElapsedSec = 0L
        binding.tvTimer.text = "Time: 0s"
        startTimer()
    }
}
