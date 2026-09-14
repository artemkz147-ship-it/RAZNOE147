package com.partymotion.playground

import android.app.Activity
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout

class MainActivity : Activity() {
    private lateinit var gameView: PlaygroundView
    private lateinit var renderView: Physics3DView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        renderView = Physics3DView(this)
        gameView = PlaygroundView(this, renderView)
        setContentView(FrameLayout(this).apply {
            addView(renderView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(gameView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        })
    }

    override fun onResume() {
        super.onResume()
        if (::renderView.isInitialized) renderView.onResume()
        if (::gameView.isInitialized) gameView.resumeSensors()
    }

    override fun onPause() {
        if (::gameView.isInitialized) gameView.pauseSensors()
        if (::renderView.isInitialized) renderView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::gameView.isInitialized) gameView.release()
        super.onDestroy()
    }
}
