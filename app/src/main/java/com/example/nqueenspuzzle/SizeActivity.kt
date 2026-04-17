package com.example.nqueenspuzzle

import android.content.Intent
import android.os.Bundle
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import com.example.nqueenspuzzle.databinding.ActivitySizeBinding

class SizeActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySizeBinding

    // Allowed range
    private val MIN_N = 4
    private val MAX_N = 50
    private val DEFAULT_N = 8

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySizeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNumberPicker()
        binding.btnStart.setOnClickListener {
            val chosen = binding.npSize.value
            startGameWithSize(chosen)
        }
    }

    private fun setupNumberPicker() {
        val np: NumberPicker = binding.npSize
        np.minValue = MIN_N
        np.maxValue = MAX_N
        np.wrapSelectorWheel = true
        np.value = DEFAULT_N

        // Optional: show values as strings (not necessary here)
        // val values = (MIN_N..MAX_N).map { it.toString() }.toTypedArray()
        // np.displayedValues = values
    }

    private fun startGameWithSize(n: Int) {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra(GameActivity.EXTRA_N, n)
        startActivity(intent)
    }
}
