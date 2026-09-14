package com.partymotion.playground

import android.content.Context
import android.opengl.GLSurfaceView
import java.util.concurrent.atomic.AtomicReference

class Physics3DView(context: Context) : GLSurfaceView(context) {
    private val frame = AtomicReference<RenderFrameSnapshot?>(null)
    private val sceneRenderer: Physics3DRenderer

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        sceneRenderer = Physics3DRenderer(context.applicationContext, frame)
        setRenderer(sceneRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun submitSnapshot(snapshot: RenderFrameSnapshot) {
        frame.set(snapshot)
    }
}
