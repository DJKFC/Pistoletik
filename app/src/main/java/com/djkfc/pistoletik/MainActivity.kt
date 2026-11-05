package com.djkfc.pistoletik

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager

class MainActivity : Activity() {
    private lateinit var view: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        view = GameView(this)
        setContentView(view)
        hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onPause() {
        super.onPause()
        synchronized(view.game) { view.game.onPause() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val handled = synchronized(view.game) { view.game.onBack() }
        if (!handled) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}

class GameView(ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback, Runnable {
    val game = Game(ctx)
    @Volatile private var running = false
    private var thread: Thread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this, "game-loop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(game) { game.resize(width, height) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = (now - last) / 1_000_000_000f
            last = now
            var cv: Canvas? = null
            try {
                cv = if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
                if (cv != null) {
                    synchronized(game) {
                        game.update(dt)
                        game.draw(cv)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (cv != null) {
                    try { holder.unlockCanvasAndPost(cv) } catch (_: Exception) {}
                }
            }
            val spent = (System.nanoTime() - now) / 1_000_000L
            if (spent < 8L) {
                try { Thread.sleep(8L - spent) } catch (_: InterruptedException) {}
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        synchronized(game) { game.touch(event) }
        return true
    }
}
