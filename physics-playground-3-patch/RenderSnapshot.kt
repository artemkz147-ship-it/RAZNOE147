package com.partymotion.playground

import com.partymotion.physics.*

enum class RenderJointKind { ROPE, SPRING, HINGE, WELD, MOTOR, PISTON }

data class RenderBodySnapshot(
    val id: Long, val x: Float, val y: Float, val angle: Float,
    val width: Float, val height: Float, val radius: Float, val circle: Boolean,
    val isStatic: Boolean, val sleeping: Boolean, val material: MaterialStyle,
    val role: String?, val integrity: Float, val maxIntegrity: Float,
    val vx: Float, val vy: Float,
)

data class RenderJointSnapshot(
    val kind: RenderJointKind,
    val ax: Float, val ay: Float, val bx: Float, val by: Float,
    val stress: Float,
)

data class RenderParticleSnapshot(val x: Float, val y: Float, val life: Float, val color: Int)

data class RenderFrameSnapshot(
    val worldWidth: Float, val worldHeight: Float,
    val viewportWorldWidth: Float, val viewportWorldHeight: Float,
    val cameraX: Float, val cameraY: Float, val cameraZoom: Float, val shake: Float,
    val goalX: Float?, val goalY: Float?,
    val bodies: List<RenderBodySnapshot>, val joints: List<RenderJointSnapshot>,
    val particles: List<RenderParticleSnapshot>,
)

fun Body.toRenderSnapshot(): RenderBodySnapshot {
    val tag = userData as? RenderTag
    val material = tag?.material ?: MaterialStyle.STEEL
    return when (val s = shape) {
        is Shape.Circle -> RenderBodySnapshot(
            id, position.x, position.y, angle, s.radius * 2f, s.radius * 2f, s.radius, true,
            isStatic, isSleeping, material, tag?.role,
            tag?.integrity ?: material.strength, tag?.maxIntegrity ?: material.strength,
            velocity.x, velocity.y,
        )
        is Shape.Box -> RenderBodySnapshot(
            id, position.x, position.y, angle, s.halfWidth * 2f, s.halfHeight * 2f, 0f, false,
            isStatic, isSleeping, material, tag?.role,
            tag?.integrity ?: material.strength, tag?.maxIntegrity ?: material.strength,
            velocity.x, velocity.y,
        )
    }
}

fun Joint.toRenderSnapshot(): RenderJointSnapshot {
    val kind = when (this) {
        is DistanceConstraint -> RenderJointKind.ROPE
        is SpringJoint -> RenderJointKind.SPRING
        is HingeJoint -> RenderJointKind.HINGE
        is WeldJoint -> RenderJointKind.WELD
        is MotorJoint -> RenderJointKind.MOTOR
        is PistonJoint -> RenderJointKind.PISTON
        else -> RenderJointKind.ROPE
    }
    return RenderJointSnapshot(kind, a.position.x, a.position.y, b.position.x, b.position.y, lastStrain.coerceIn(0f, 1.5f))
}
