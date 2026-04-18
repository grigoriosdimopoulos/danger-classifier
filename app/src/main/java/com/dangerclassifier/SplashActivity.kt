package com.dangerclassifier

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    // Pool of ancient Greek quotes. Each entry is (quote, attribution).
    private val quotes = listOf(
        Pair(
            "The bravest are surely those who have the clearest vision of what is before them — glory and danger alike — and yet notwithstanding, go out to meet it.",
            "— Thucydides, History of the Peloponnesian War (431 BC)"
        ),
        Pair(
            "Courage is knowing what not to fear.",
            "— Plato, Protagoras (399 BC)"
        ),
        Pair(
            "We cannot learn without pain.",
            "— Aristotle, Nicomachean Ethics (350 BC)"
        ),
        Pair(
            "It is not death or pain that is to be dreaded, but the fear of pain or death.",
            "— Epictetus, Discourses (108 AD)"
        ),
        Pair(
            "Be convinced that to be happy means to be free, and that to be free means to be brave.",
            "— Pericles, via Thucydides (431 BC)"
        ),
        Pair(
            "The first and greatest victory is to conquer yourself; to be conquered by yourself is of all things most shameful and vile.",
            "— Plato, The Laws (360 BC)"
        ),
        Pair(
            "What we fear doing most is usually what we most need to do.",
            "— Aristotle, Rhetoric (350 BC)"
        ),
        Pair(
            "No man is free who is not master of himself.",
            "— Epictetus, Enchiridion (125 AD)"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContentView(R.layout.activity_splash)

        val logoBlock          = findViewById<View>(R.id.logoBlock)
        val divider            = findViewById<View>(R.id.divider)
        val quoteBlock         = findViewById<View>(R.id.quoteBlock)
        val progressContainer  = findViewById<View>(R.id.progressBarContainer)
        val quoteText          = findViewById<TextView>(R.id.quoteText)
        val attributionText    = findViewById<TextView>(R.id.attributionText)
        val readingProgress    = findViewById<ProgressBar>(R.id.readingProgress)

        // Pick a random quote
        val (quote, attribution) = quotes.random()
        quoteText.text       = quote
        attributionText.text = attribution

        // Reading time: 3.5s base + 20ms per character, capped at 7s
        val readMs = (3500L + quote.length * 20L).coerceAtMost(7000L)

        // --- Animations ---

        // Logo rises and fades in
        logoBlock.translationY = 36f
        logoBlock.animate()
            .alpha(1f).translationY(0f)
            .setDuration(750).setStartDelay(100)
            .start()

        // Divider fades in
        divider.animate()
            .alpha(1f)
            .setDuration(500).setStartDelay(650)
            .start()

        // Quote fades in
        quoteBlock.animate()
            .alpha(1f)
            .setDuration(900).setStartDelay(900)
            .start()

        // Progress container fades in
        progressContainer.animate()
            .alpha(1f)
            .setDuration(500).setStartDelay(1200)
            .start()

        // Progress bar fills over the full reading duration
        window.decorView.postDelayed({
            ObjectAnimator.ofInt(readingProgress, "progress", 0, 1000).apply {
                duration        = readMs - 1200L   // start filling when quote is visible
                interpolator    = LinearInterpolator()
                start()
            }
        }, 1200)

        // Pulse the logo gently while waiting (subtle scale breathe)
        logoBlock.postDelayed({
            ValueAnimator.ofFloat(1f, 1.04f, 1f).apply {
                duration      = 2400
                repeatCount   = ValueAnimator.INFINITE
                repeatMode    = ValueAnimator.RESTART
                addUpdateListener { anim ->
                    val v = anim.animatedValue as Float
                    logoBlock.scaleX = v
                    logoBlock.scaleY = v
                }
                start()
            }
        }, 900)

        // Navigate to MainActivity after reading time
        window.decorView.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, readMs)
    }
}
