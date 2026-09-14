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
import kotlin.math.ceil
import kotlin.math.sqrt

class Physics3DRenderer(
    private val context: Context,
    private val frameRef: AtomicReference<RenderFrameSnapshot?>,
) : GLSurfaceView.Renderer {
    private data class GpuMesh(val vbo: Int, val count: Int)
    private data class Surface(
        val color: FloatArray,
        val alpha: Float,
        val metallic: Float,
        val roughness: Float,
        val emissive: Float = 0f,
    )

    private val meshes = HashMap<String, GpuMesh>()
    private val projection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var program = 0
    private var mvpLoc = -1
    private var modelLoc = -1
    private var colorLoc = -1
    private var alphaLoc = -1
    private var metallicLoc = -1
    private var roughnessLoc = -1
    private var emissiveLoc = -1
    private var damageLoc = -1
    private var timeLoc = -1
    private var frameTime = 0f

    private val names = listOf(
        "ball", "wheel", "wheel2", "crate", "steel_block", "weight", "ice_block", "glass_block",
        "jelly", "beam", "motor", "piston", "machine", "machine2", "barrel", "pipe", "gear", "platform"
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.008f, 0.014f, 0.028f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glEnable(GLES30.GL_DITHER)
        program = link(VERTEX, FRAGMENT)
        mvpLoc = GLES30.glGetUniformLocation(program, "uMvp")
        modelLoc = GLES30.glGetUniformLocation(program, "uModel")
        colorLoc = GLES30.glGetUniformLocation(program, "uBaseColor")
        alphaLoc = GLES30.glGetUniformLocation(program, "uAlpha")
        metallicLoc = GLES30.glGetUniformLocation(program, "uMetallic")
        roughnessLoc = GLES30.glGetUniformLocation(program, "uRoughness")
        emissiveLoc = GLES30.glGetUniformLocation(program, "uEmissive")
        damageLoc = GLES30.glGetUniformLocation(program, "uDamage")
        timeLoc = GLES30.glGetUniformLocation(program, "uTime")
        names.forEach { name ->
            runCatching { context.assets.open("models/$name.obj").use { upload(ObjMesh.parse(it)) } }
                .onSuccess { meshes[name] = it }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    override fun onDrawFrame(gl: GL10?) {
        frameTime += 1f / 60f
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val f = frameRef.get() ?: return
        val hw = f.viewportWorldWidth / (2f * f.cameraZoom)
        val hh = f.viewportWorldHeight / (2f * f.cameraZoom)
        val shakeX = f.shake * 0.0020f
        val shakeY = f.shake * 0.0013f
        Matrix.orthoM(
            projection, 0,
            f.cameraX - hw + shakeX, f.cameraX + hw + shakeX,
            f.cameraY + hh + shakeY, f.cameraY - hh + shakeY,
            -24f, 24f
        )
        GLES30.glUseProgram(program)
        GLES30.glUniform1f(timeLoc, frameTime)

        drawEnvironment(f, hw, hh)

        // Contact shadows: flattened, offset silhouettes anchor models to the scene.
        GLES30.glDepthMask(false)
        f.bodies.take(180).forEach { drawBody(it, shadow = true) }
        GLES30.glDepthMask(true)

        drawMotionTrails(f)
        f.joints.forEach(::drawJoint)

        val opaque = f.bodies.filter { it.material != MaterialStyle.GLASS && it.material != MaterialStyle.ICE }
        val transparent = f.bodies.filter { it.material == MaterialStyle.GLASS || it.material == MaterialStyle.ICE }
        opaque.forEach { drawBody(it, shadow = false) }

        // Damaged bodies get a subtle hot edge rather than a flat color swap.
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        opaque.filter { damageOf(it) > 0.35f }.take(60).forEach { drawDamageGlow(it) }
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        GLES30.glDepthMask(false)
        transparent.sortedByDescending { it.y }.forEach { drawBody(it, shadow = false) }
        GLES30.glDepthMask(true)

        drawGoal(f)
        drawParticles(f)
    }

    private fun drawEnvironment(f: RenderFrameSnapshot, hw: Float, hh: Float) {
        val slab = meshes["steel_block"] ?: return
        val beam = meshes["beam"] ?: slab
        val cx = f.cameraX
        val cy = f.cameraY

        // Deep blue industrial back wall.
        draw(
            slab, cx, cy, -10.5f, 0f,
            hw * 2.35f, hh * 2.35f, .10f,
            Surface(floatArrayOf(.018f, .032f, .058f), 1f, .58f, .76f), 0f, false
        )

        // Recessed grid gives scale and depth without affecting physics or touch mapping.
        val spacing = 1.6f
        val xStart = (cx - hw * 1.2f)
        val xCount = ceil((hw * 2.4f) / spacing).toInt().coerceAtMost(22)
        repeat(xCount + 1) { i ->
            val x = xStart + i * spacing
            draw(beam, x, cy, -9.4f, 1.5707963f, hh * 2.4f, .025f, .025f,
                Surface(floatArrayOf(.07f,.13f,.20f), .26f, .7f, .55f, .05f), 0f, false)
        }
        val yStart = cy - hh * 1.2f
        val yCount = ceil((hh * 2.4f) / spacing).toInt().coerceAtMost(22)
        repeat(yCount + 1) { i ->
            val y = yStart + i * spacing
            draw(beam, cx, y, -9.35f, 0f, hw * 2.4f, .025f, .025f,
                Surface(floatArrayOf(.07f,.13f,.20f), .23f, .7f, .55f, .04f), 0f, false)
        }

        // Bottom platform and luminous safety strips.
        val floorY = cy + hh * .92f
        draw(slab, cx, floorY, -2.8f, 0f, hw * 2.4f, hh * .20f, .24f,
            Surface(floatArrayOf(.055f,.065f,.080f),1f,.82f,.30f),0f,true)
        draw(beam, cx, floorY - hh * .10f, -2.4f, 0f, hw * 2.35f, .035f, .035f,
            Surface(floatArrayOf(.12f,.62f,1.0f),.72f,.3f,.22f,1.7f),0f,false)
    }

    private fun drawBody(b: RenderBodySnapshot, shadow: Boolean) {
        val mesh = meshes[modelFor(b)] ?: meshes[if (b.circle) "ball" else "steel_block"] ?: return
        if (shadow) {
            val speed = sqrt(b.vx*b.vx + b.vy*b.vy)
            val stretch = (1f + speed * .006f).coerceAtMost(1.18f)
            draw(
                mesh, b.x + .08f, b.y + .11f, -1.8f, b.angle,
                b.width * 1.03f * stretch, b.height * .92f, depth(b) * .16f,
                Surface(floatArrayOf(.002f,.004f,.008f), .30f, 0f, 1f), 0f, false
            )
            return
        }
        draw(
            mesh, b.x, b.y, bodyDepth(b), b.angle,
            b.width, b.height, depth(b), surface(b.material, b.sleeping), damageOf(b), true
        )
    }

    private fun drawDamageGlow(b: RenderBodySnapshot) {
        val mesh = meshes[modelFor(b)] ?: return
        val d = damageOf(b)
        draw(
            mesh, b.x, b.y, bodyDepth(b) + .03f, b.angle,
            b.width * 1.015f, b.height * 1.015f, depth(b) * 1.015f,
            Surface(floatArrayOf(1f,.16f,.025f), (.05f + d*.16f).coerceAtMost(.19f), 0f, .5f, 1.8f), d, true
        )
    }

    private fun drawMotionTrails(f: RenderFrameSnapshot) {
        GLES30.glDepthMask(false)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        f.bodies.asSequence().filter {
            !it.isStatic && (it.vx*it.vx + it.vy*it.vy) > 55f
        }.take(24).forEach { b ->
            val mesh = meshes[modelFor(b)] ?: return@forEach
            val visual = surface(b.material, b.sleeping)
            for (i in 1..3) {
                val k = i * .014f
                val alpha = (.10f / i).coerceAtMost(.08f)
                draw(
                    mesh, b.x - b.vx*k, b.y - b.vy*k, bodyDepth(b)-.05f*i, b.angle,
                    b.width, b.height, depth(b),
                    visual.copy(alpha = alpha, emissive = .38f), damageOf(b), true
                )
            }
        }
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(true)
    }

    private fun drawJoint(j: RenderJointSnapshot) {
        val dx = j.bx - j.ax
        val dy = j.by - j.ay
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.04f)
        val cx = (j.ax + j.bx) * .5f
        val cy = (j.ay + j.by) * .5f
        val angle = atan2(dy, dx)
        val stress = (j.stress / 1.2f).coerceIn(0f, 1f)
        when (j.kind) {
            RenderJointKind.MOTOR -> meshes["motor"]?.let {
                draw(it,j.bx,j.by,.34f,angle,.62f,.62f,.54f,Surface(floatArrayOf(.96f,.22f,.035f),1f,.82f,.22f,.08f),stress,true)
            }
            RenderJointKind.PISTON -> meshes["piston"]?.let {
                draw(it,cx,cy,.28f,angle,length,.42f,.46f,Surface(floatArrayOf(.58f,.66f,.76f),1f,.93f,.18f),stress,true)
            }
            RenderJointKind.HINGE -> meshes["gear"]?.let {
                draw(it,cx,cy,.38f,0f,.32f,.32f,.32f,Surface(floatArrayOf(1f,.56f,.04f),1f,.78f,.23f,.10f),stress,true)
            } ?: meshes["ball"]?.let {
                draw(it,cx,cy,.38f,0f,.28f,.28f,.28f,Surface(floatArrayOf(1f,.56f,.04f),1f,.75f,.25f),stress,true)
            }
            RenderJointKind.SPRING -> meshes["pipe"]?.let {
                draw(it,cx,cy,.20f,angle,length,.10f,.11f,Surface(floatArrayOf(.10f,.67f,1f),1f,.80f,.18f,.08f),stress,true)
            } ?: meshes["beam"]?.let {
                draw(it,cx,cy,.20f,angle,length,.09f,.10f,Surface(floatArrayOf(.10f,.67f,1f),1f,.80f,.20f),stress,true)
            }
            RenderJointKind.WELD -> meshes["beam"]?.let {
                draw(it,cx,cy,.18f,angle,length,.14f,.14f,Surface(floatArrayOf(1f,.32f,.03f),1f,.84f,.24f,.05f),stress,true)
            }
            RenderJointKind.ROPE -> meshes["beam"]?.let {
                draw(it,cx,cy,.10f,angle,length,.050f,.060f,
                    Surface(floatArrayOf(.28f + stress*.62f,.58f-stress*.33f,.82f-stress*.50f),1f,.18f,.72f),stress,true)
            }
        }
    }

    private fun drawGoal(f: RenderFrameSnapshot) {
        val x = f.goalX ?: return
        val y = f.goalY ?: return
        val mesh = meshes["gear"] ?: meshes["wheel"] ?: return
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        draw(mesh,x,y,.14f,frameTime*.55f,1.28f,1.28f,.24f,
            Surface(floatArrayOf(.04f,1f,.37f),.66f,.35f,.16f,2.2f),0f,true)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun drawParticles(f: RenderFrameSnapshot) {
        val mesh = meshes["ball"] ?: return
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(false)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        f.particles.takeLast(180).forEach { p ->
            val c = floatArrayOf(
                ((p.color shr 16) and 255)/255f,
                ((p.color shr 8) and 255)/255f,
                (p.color and 255)/255f
            )
            val life = p.life.coerceIn(0f,1f)
            val s = .025f + life*.075f
            draw(mesh,p.x,p.y,.78f,0f,s,s,s,Surface(c,life*.86f,.15f,.24f,1.9f),0f,false)
            if (life > .45f) {
                draw(mesh,p.x,p.y,.76f,0f,s*2.3f,s*2.3f,s*2.3f,Surface(c,life*.12f,0f,.5f,1.4f),0f,false)
            }
        }
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    private fun draw(
        mesh: GpuMesh,
        x: Float,
        y: Float,
        z: Float,
        angle: Float,
        sx: Float,
        sy: Float,
        sz: Float,
        surface: Surface,
        damage: Float,
        tilt: Boolean,
    ) {
        Matrix.setIdentityM(model,0)
        Matrix.translateM(model,0,x,y,z)
        Matrix.rotateM(model,0,angle*57.29578f,0f,0f,1f)
        if (tilt) {
            Matrix.rotateM(model,0,12f,1f,0f,0f)
            Matrix.rotateM(model,0,-10f,0f,1f,0f)
        }
        Matrix.scaleM(model,0,sx,sy,sz)
        Matrix.multiplyMM(mvp,0,projection,0,model,0)
        GLES30.glUniformMatrix4fv(mvpLoc,1,false,mvp,0)
        GLES30.glUniformMatrix4fv(modelLoc,1,false,model,0)
        GLES30.glUniform3f(colorLoc,surface.color[0],surface.color[1],surface.color[2])
        GLES30.glUniform1f(alphaLoc,surface.alpha)
        GLES30.glUniform1f(metallicLoc,surface.metallic)
        GLES30.glUniform1f(roughnessLoc,surface.roughness)
        GLES30.glUniform1f(emissiveLoc,surface.emissive)
        GLES30.glUniform1f(damageLoc,damage.coerceIn(0f,1f))
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,mesh.vbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,24,0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,24,12)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,mesh.count)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun modelFor(b: RenderBodySnapshot): String {
        b.role?.let { r ->
            if (r.contains("wheel",true)) return if (b.id % 2L == 0L) "wheel" else "wheel2"
            if (r.contains("chassis",true) || r.contains("vehicle",true)) return if (b.id % 2L == 0L) "machine" else "machine2"
            if (r.contains("catapult",true)) return "beam"
            if (r.contains("motor",true)) return "motor"
            if (r.contains("piston",true)) return "piston"
        }
        if (b.circle) return when (b.material) {
            MaterialStyle.JELLY -> "jelly"
            MaterialStyle.HEAVY -> "weight"
            MaterialStyle.STEEL -> if (b.id % 3L == 0L) "gear" else "ball"
            else -> "ball"
        }
        if (b.width > b.height * 2.15f) return if (b.material == MaterialStyle.STEEL) "platform" else "beam"
        if (b.height > b.width * 1.7f && (b.material == MaterialStyle.STEEL || b.material == MaterialStyle.HEAVY)) return "pipe"
        return when (b.material) {
            MaterialStyle.WOOD -> "crate"
            MaterialStyle.STEEL -> when ((b.id % 4L).toInt()) { 0 -> "steel_block"; 1 -> "barrel"; 2 -> "machine2"; else -> "steel_block" }
            MaterialStyle.HEAVY -> if (b.id % 2L == 0L) "weight" else "barrel"
            MaterialStyle.ICE -> "ice_block"
            MaterialStyle.GLASS -> "glass_block"
            MaterialStyle.JELLY -> "jelly"
            MaterialStyle.RUBBER -> "steel_block"
        }
    }

    private fun bodyDepth(b: RenderBodySnapshot): Float = when {
        b.role?.contains("wheel",true)==true -> .58f
        b.role?.contains("vehicle",true)==true || b.role?.contains("chassis",true)==true -> .34f
        else -> .26f
    }

    private fun depth(b: RenderBodySnapshot): Float = when {
        b.role?.contains("wheel",true)==true -> b.width*.48f
        b.circle -> b.width*.74f
        b.width > b.height*2f -> b.height*1.22f
        else -> minOf(b.width,b.height)*.80f
    }

    private fun damageOf(b: RenderBodySnapshot): Float {
        if (b.maxIntegrity <= .0001f) return 0f
        return (1f - b.integrity / b.maxIntegrity).coerceIn(0f,1f)
    }

    private fun surface(m: MaterialStyle, sleep: Boolean): Surface {
        val d = if (sleep) .76f else 1f
        return when(m) {
            MaterialStyle.RUBBER -> Surface(floatArrayOf(.24f*d,.032f*d,.025f*d),1f,.04f,.82f)
            MaterialStyle.WOOD -> Surface(floatArrayOf(.52f*d,.21f*d,.055f*d),1f,.02f,.72f)
            MaterialStyle.STEEL -> Surface(floatArrayOf(.42f*d,.50f*d,.61f*d),1f,.92f,.24f)
            MaterialStyle.ICE -> Surface(floatArrayOf(.40f*d,.80f*d,1f*d),.70f,.06f,.13f,.05f)
            MaterialStyle.GLASS -> Surface(floatArrayOf(.60f*d,.91f*d,1f*d),.43f,.12f,.08f,.03f)
            MaterialStyle.JELLY -> Surface(floatArrayOf(.70f*d,.06f*d,.82f*d),.87f,.01f,.46f,.06f)
            MaterialStyle.HEAVY -> Surface(floatArrayOf(.095f*d,.115f*d,.16f*d),1f,.94f,.19f)
        }
    }

    private fun upload(mesh: ObjMesh): GpuMesh {
        val id=IntArray(1)
        GLES30.glGenBuffers(1,id,0)
        val data=ByteBuffer.allocateDirect(mesh.interleaved.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        data.put(mesh.interleaved).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,id[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,mesh.interleaved.size*4,data,GLES30.GL_STATIC_DRAW)
        return GpuMesh(id[0],mesh.vertexCount)
    }

    private fun link(v:String,f:String):Int {
        val vs=shader(GLES30.GL_VERTEX_SHADER,v)
        val fs=shader(GLES30.GL_FRAGMENT_SHADER,f)
        val p=GLES30.glCreateProgram()
        GLES30.glAttachShader(p,vs); GLES30.glAttachShader(p,fs); GLES30.glLinkProgram(p)
        val ok=IntArray(1)
        GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0)
        if(ok[0]==0) error(GLES30.glGetProgramInfoLog(p))
        GLES30.glDeleteShader(vs); GLES30.glDeleteShader(fs)
        return p
    }

    private fun shader(type:Int,src:String):Int {
        val s=GLES30.glCreateShader(type)
        GLES30.glShaderSource(s,src); GLES30.glCompileShader(s)
        val ok=IntArray(1)
        GLES30.glGetShaderiv(s,GLES30.GL_COMPILE_STATUS,ok,0)
        if(ok[0]==0) error(GLES30.glGetShaderInfoLog(s))
        return s
    }

    companion object {
        private const val VERTEX = """#version 300 es
precision highp float;
layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
uniform mat4 uMvp;
uniform mat4 uModel;
out vec3 vNormal;
out vec3 vWorldPos;
void main(){
    vec4 wp=uModel*vec4(aPosition,1.0);
    vWorldPos=wp.xyz;
    vNormal=normalize(mat3(uModel)*aNormal);
    gl_Position=uMvp*vec4(aPosition,1.0);
}
"""
        private const val FRAGMENT = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec3 vWorldPos;
uniform vec3 uBaseColor;
uniform float uAlpha;
uniform float uMetallic;
uniform float uRoughness;
uniform float uEmissive;
uniform float uDamage;
uniform float uTime;
out vec4 fragColor;

float sat(float x){ return clamp(x,0.0,1.0); }
vec3 fresnelSchlick(float cosTheta, vec3 F0){ return F0+(1.0-F0)*pow(1.0-cosTheta,5.0); }

void main(){
    vec3 N=normalize(vNormal);
    vec3 V=normalize(vec3(0.18,-0.24,1.35));
    vec3 L1=normalize(vec3(-0.42,-0.62,0.88));
    vec3 L2=normalize(vec3(0.72,0.18,0.58));
    vec3 H1=normalize(V+L1);
    vec3 H2=normalize(V+L2);
    float ndl1=max(dot(N,L1),0.0);
    float ndl2=max(dot(N,L2),0.0);
    float ndv=max(dot(N,V),0.001);
    float shininess=mix(110.0,8.0,sat(uRoughness));
    vec3 F0=mix(vec3(0.04),uBaseColor,sat(uMetallic));
    vec3 F1=fresnelSchlick(max(dot(H1,V),0.0),F0);
    vec3 F2=fresnelSchlick(max(dot(H2,V),0.0),F0);
    float spec1=pow(max(dot(N,H1),0.0),shininess)*(1.0-uRoughness*.72);
    float spec2=pow(max(dot(N,H2),0.0),shininess*.72)*(1.0-uRoughness*.78);
    vec3 diffuse=uBaseColor*(1.0-uMetallic*.70);
    vec3 sky=mix(vec3(.018,.032,.060),vec3(.12,.21,.33),sat(N.y*.5+.5));
    vec3 color=diffuse*(sky*.72 + ndl1*vec3(1.03,.89,.76) + ndl2*vec3(.20,.43,.76)*.43);
    color += F1*spec1*1.25 + F2*spec2*.48;
    float rim=pow(1.0-ndv,3.0);
    color += rim*mix(vec3(.03,.11,.24),uBaseColor,.25)*(.22+.58*uMetallic);
    // Damage darkens recesses and adds a hot fractured edge without replacing the material.
    float fracture=step(.42,uDamage)*pow(abs(sin(vWorldPos.x*19.0+vWorldPos.y*13.0+vWorldPos.z*17.0)),18.0);
    color=mix(color,color*.44,uDamage*.30);
    color += vec3(1.0,.12,.015)*fracture*uDamage*.55;
    color += uBaseColor*uEmissive;
    // Mild height fog and filmic tone mapping keep highlights controlled on phone displays.
    float fog=sat((abs(vWorldPos.z)-3.0)/14.0);
    color=mix(color,vec3(.009,.017,.030),fog*.38);
    color=color/(color+vec3(1.0));
    color=pow(color,vec3(1.0/2.2));
    float fres=pow(1.0-ndv,2.4);
    float alpha=clamp(uAlpha + fres*(1.0-uAlpha)*.48,0.0,1.0);
    fragColor=vec4(color,alpha);
}
"""
    }
}
