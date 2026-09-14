package com.partymotion.playground

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.sqrt

class Physics3DRenderer(
    private val context: Context,
    private val frameRef: AtomicReference<RenderFrameSnapshot?>,
) : GLSurfaceView.Renderer {
    private data class GpuMesh(val vbo: Int, val count: Int)
    private val meshes = HashMap<String, GpuMesh>()
    private val projection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var program = 0
    private var mvpLoc = -1
    private var modelLoc = -1
    private var colorLoc = -1
    private var alphaLoc = -1
    private var glossLoc = -1

    private val names = listOf("ball", "wheel", "crate", "steel_block", "weight", "ice_block", "glass_block", "jelly", "beam", "motor", "piston", "machine")

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.015f, 0.023f, 0.038f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        program = link(VERTEX, FRAGMENT)
        mvpLoc = GLES30.glGetUniformLocation(program, "uMvp")
        modelLoc = GLES30.glGetUniformLocation(program, "uModel")
        colorLoc = GLES30.glGetUniformLocation(program, "uColor")
        alphaLoc = GLES30.glGetUniformLocation(program, "uAlpha")
        glossLoc = GLES30.glGetUniformLocation(program, "uGloss")
        names.forEach { name ->
            runCatching { context.assets.open("models/$name.obj").use { upload(ObjMesh.parse(it)) } }
                .onSuccess { meshes[name] = it }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = GLES30.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val f = frameRef.get() ?: return
        val hw = f.viewportWorldWidth / (2f * f.cameraZoom)
        val hh = f.viewportWorldHeight / (2f * f.cameraZoom)
        val shake = f.shake * 0.002f
        Matrix.orthoM(projection, 0, f.cameraX - hw + shake, f.cameraX + hw + shake, f.cameraY + hh, f.cameraY - hh, -20f, 20f)
        GLES30.glUseProgram(program)

        // Soft 3D silhouettes make height/depth obvious while preserving exact 2D physics positions.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        f.bodies.forEach { drawBody(it, true) }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        f.joints.forEach(::drawJoint)
        f.bodies.filter { it.material != MaterialStyle.GLASS && it.material != MaterialStyle.ICE }.forEach { drawBody(it, false) }
        f.bodies.filter { it.material == MaterialStyle.GLASS || it.material == MaterialStyle.ICE }.forEach { drawBody(it, false) }
        drawGoal(f)
        drawParticles(f)
    }

    private fun drawBody(b: RenderBodySnapshot, shadow: Boolean) {
        val mesh = meshes[modelFor(b)] ?: meshes[if (b.circle) "ball" else "steel_block"] ?: return
        val visual = visual(b.material, b.sleeping)
        if (shadow) {
            draw(mesh, b.x + 0.07f, b.y + 0.10f, b.angle, b.width * 1.02f, b.height * 1.02f, depth(b) * 0.45f, floatArrayOf(0.004f,0.006f,0.011f), 0.29f, 0.02f)
        } else {
            draw(mesh, b.x, b.y, b.angle, b.width, b.height, depth(b), visual.first, visual.second, visual.third)
        }
    }

    private fun drawJoint(j: RenderJointSnapshot) {
        val dx = j.bx - j.ax; val dy = j.by - j.ay
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(0.04f)
        val cx = (j.ax + j.bx) * 0.5f; val cy = (j.ay + j.by) * 0.5f
        val angle = atan2(dy, dx)
        val stress = (j.stress / 1.2f).coerceIn(0f, 1f)
        when (j.kind) {
            RenderJointKind.MOTOR -> meshes["motor"]?.let { draw(it, j.bx, j.by, angle, .58f,.58f,.5f, floatArrayOf(.95f,.28f,.08f),1f,1f) }
            RenderJointKind.PISTON -> meshes["piston"]?.let { draw(it,cx,cy,angle,length,.38f,.42f,floatArrayOf(.68f,.74f,.82f),1f,.95f) }
            RenderJointKind.HINGE -> meshes["ball"]?.let { draw(it,cx,cy,0f,.28f,.28f,.28f,floatArrayOf(1f,.67f,.08f),1f,.8f) }
            RenderJointKind.SPRING -> meshes["beam"]?.let { draw(it,cx,cy,angle,length,.09f,.10f,floatArrayOf(.20f,.78f,1f),1f,.75f) }
            RenderJointKind.WELD -> meshes["beam"]?.let { draw(it,cx,cy,angle,length,.13f,.13f,floatArrayOf(1f,.46f,.08f),1f,.8f) }
            RenderJointKind.ROPE -> meshes["beam"]?.let { draw(it,cx,cy,angle,length,.055f,.07f,floatArrayOf(.35f + stress*.55f,.65f-stress*.35f,.95f-stress*.55f),1f,.25f) }
        }
    }

    private fun drawGoal(f: RenderFrameSnapshot) {
        val x = f.goalX ?: return; val y = f.goalY ?: return
        meshes["wheel"]?.let { draw(it,x,y,0f,1.18f,1.18f,.20f,floatArrayOf(.12f,1f,.42f),.62f,.7f) }
    }

    private fun drawParticles(f: RenderFrameSnapshot) {
        val mesh = meshes["ball"] ?: return
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        f.particles.takeLast(120).forEach { p ->
            val c = floatArrayOf(((p.color shr 16) and 255)/255f, ((p.color shr 8) and 255)/255f, (p.color and 255)/255f)
            val s = .035f + p.life.coerceIn(0f,1f) * .05f
            draw(mesh,p.x,p.y,0f,s,s,s,c,p.life.coerceIn(0f,1f),.12f,false)
        }
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    private fun draw(mesh: GpuMesh, x: Float, y: Float, angle: Float, sx: Float, sy: Float, sz: Float, color: FloatArray, alpha: Float, gloss: Float, tilt: Boolean = true) {
        Matrix.setIdentityM(model,0)
        Matrix.translateM(model,0,x,y,0f)
        Matrix.rotateM(model,0,angle * 57.29578f,0f,0f,1f)
        if (tilt) { Matrix.rotateM(model,0,10f,1f,0f,0f); Matrix.rotateM(model,0,-8f,0f,1f,0f) }
        Matrix.scaleM(model,0,sx,sy,sz)
        Matrix.multiplyMM(mvp,0,projection,0,model,0)
        GLES30.glUniformMatrix4fv(mvpLoc,1,false,mvp,0)
        GLES30.glUniformMatrix4fv(modelLoc,1,false,model,0)
        GLES30.glUniform3f(colorLoc,color[0],color[1],color[2])
        GLES30.glUniform1f(alphaLoc,alpha); GLES30.glUniform1f(glossLoc,gloss)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,mesh.vbo)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,24,0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,24,12)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,mesh.count)
        GLES30.glDisableVertexAttribArray(0); GLES30.glDisableVertexAttribArray(1)
    }

    private fun modelFor(b: RenderBodySnapshot): String {
        b.role?.let { r ->
            if (r.contains("wheel",true)) return "wheel"
            if (r.contains("chassis",true) || r.contains("vehicle",true)) return "machine"
            if (r.contains("catapult",true)) return "beam"
            if (r.contains("motor",true)) return "motor"
            if (r.contains("piston",true)) return "piston"
        }
        if (b.circle) return when (b.material) { MaterialStyle.JELLY -> "jelly"; MaterialStyle.HEAVY -> "weight"; else -> "ball" }
        if (b.width > b.height * 2.15f) return "beam"
        return when (b.material) {
            MaterialStyle.WOOD -> "crate"; MaterialStyle.STEEL -> "steel_block"; MaterialStyle.HEAVY -> "weight"
            MaterialStyle.ICE -> "ice_block"; MaterialStyle.GLASS -> "glass_block"; MaterialStyle.JELLY -> "jelly"; MaterialStyle.RUBBER -> "steel_block"
        }
    }

    private fun depth(b: RenderBodySnapshot) = when { b.role?.contains("wheel",true)==true -> b.width*.46f; b.circle -> b.width*.72f; b.width > b.height*2f -> b.height*1.15f; else -> minOf(b.width,b.height)*.75f }

    private fun visual(m: MaterialStyle, sleep: Boolean): Triple<FloatArray,Float,Float> {
        val d = if (sleep) .84f else 1f
        return when(m) {
            MaterialStyle.RUBBER -> Triple(floatArrayOf(.88f*d,.14f*d,.12f*d),1f,.3f)
            MaterialStyle.WOOD -> Triple(floatArrayOf(.62f*d,.29f*d,.10f*d),1f,.2f)
            MaterialStyle.STEEL -> Triple(floatArrayOf(.48f*d,.57f*d,.68f*d),1f,1f)
            MaterialStyle.ICE -> Triple(floatArrayOf(.50f*d,.86f*d,1f*d),.72f,.8f)
            MaterialStyle.GLASS -> Triple(floatArrayOf(.70f*d,.94f*d,1f*d),.52f,1f)
            MaterialStyle.JELLY -> Triple(floatArrayOf(.76f*d,.16f*d,.92f*d),.88f,.4f)
            MaterialStyle.HEAVY -> Triple(floatArrayOf(.20f*d,.23f*d,.29f*d),1f,1f)
        }
    }

    private fun upload(mesh: ObjMesh): GpuMesh {
        val id = IntArray(1); GLES30.glGenBuffers(1,id,0)
        val data = ByteBuffer.allocateDirect(mesh.interleaved.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        data.put(mesh.interleaved).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,id[0]); GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,mesh.interleaved.size*4,data,GLES30.GL_STATIC_DRAW)
        return GpuMesh(id[0],mesh.vertexCount)
    }

    private fun link(v: String, f: String): Int {
        val vs=shader(GLES30.GL_VERTEX_SHADER,v); val fs=shader(GLES30.GL_FRAGMENT_SHADER,f)
        val p=GLES30.glCreateProgram(); GLES30.glAttachShader(p,vs); GLES30.glAttachShader(p,fs); GLES30.glLinkProgram(p)
        val ok=IntArray(1); GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0); if(ok[0]==0) error(GLES30.glGetProgramInfoLog(p))
        GLES30.glDeleteShader(vs); GLES30.glDeleteShader(fs); return p
    }
    private fun shader(type:Int, src:String):Int { val s=GLES30.glCreateShader(type); GLES30.glShaderSource(s,src); GLES30.glCompileShader(s); val ok=IntArray(1); GLES30.glGetShaderiv(s,GLES30.GL_COMPILE_STATUS,ok,0); if(ok[0]==0) error(GLES30.glGetShaderInfoLog(s)); return s }

    companion object {
        private const val VERTEX = """#version 300 es
precision mediump float; layout(location=0) in vec3 aPosition; layout(location=1) in vec3 aNormal;
uniform mat4 uMvp; uniform mat4 uModel; out vec3 vNormal;
void main(){ vNormal=normalize(mat3(uModel)*aNormal); gl_Position=uMvp*vec4(aPosition,1.0); }
"""
        private const val FRAGMENT = """#version 300 es
precision mediump float; in vec3 vNormal; uniform vec3 uColor; uniform float uAlpha; uniform float uGloss; out vec4 fragColor;
void main(){ vec3 n=normalize(vNormal); vec3 l=normalize(vec3(-.42,-.62,.88)); float d=max(dot(n,l),0.0); vec3 h=normalize(l+vec3(.15,-.2,1.0)); float s=pow(max(dot(n,h),0.0),mix(12.0,72.0,clamp(uGloss,0.0,1.0)))*uGloss; float r=pow(1.0-max(n.z,0.0),2.0)*.12; fragColor=vec4(uColor*(.28+d*.78)+vec3(s*.48+r),uAlpha); }
"""
    }
}
