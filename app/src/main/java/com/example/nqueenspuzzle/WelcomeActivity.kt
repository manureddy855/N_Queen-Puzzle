package com.example.nqueenspuzzle

import android.content.Intent
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.nqueenspuzzle.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // gentle scale animation on crown icon
        val scale = ScaleAnimation(
            0.95f, 1.05f, 0.95f, 1.05f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        scale.duration = 900
        scale.repeatMode = Animation.REVERSE
        scale.repeatCount = Animation.INFINITE
        binding.tvCrown.startAnimation(scale)

        binding.btnStart.setOnClickListener {
            startActivity(Intent(this, SizeActivity::class.java))
        }

        binding.btnAbout.setOnClickListener {
            // Show only the instructions inside About dialog
            AlertDialog.Builder(this)
                .setTitle("Instructions")
                .setMessage("1) Place N queens on the board.\n2) No two queens in the same row, column, or diagonal.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
