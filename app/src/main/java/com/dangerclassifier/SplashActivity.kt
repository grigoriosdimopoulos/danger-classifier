package com.dangerclassifier

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContentView(R.layout.activity_splash)

        val logoBlock   = findViewById<View>(R.id.logoBlock)
        val divider     = findViewById<View>(R.id.divider)
        val quoteBlock  = findViewById<View>(R.id.quoteBlock)
        val versionText = findViewById<View>(R.id.versionText)

        // Logo fades + rises in
        logoBlock.translationY = 40f
        logoBlock.animate()
            .alpha(1f).translationY(0f)
            .setDuration(700).setStartDelay(150)
            .start()

        // Divider fades in
        divider.animate()
            .alpha(1f)
            .setDuration(500).setStartDelay(600)
            .start()

        // Quote fades in
        quoteBlock.animate()
            .alpha(1f)
            .setDuration(800).setStartDelay(800)
            .start()

        // Version fades in
        versionText.animate()
            .alpha(1f)
            .setDuration(500).setStartDelay(1000)
            .start()

        // Navigate to MainActivity after 3 seconds
        window.decorView.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 3000)
    }
}
