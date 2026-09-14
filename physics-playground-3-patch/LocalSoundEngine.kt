package com.partymotion.playground

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.random.Random

class LocalSoundEngine(private val context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()

    private val banks = mapOf(
        "wood" to loadBank("sfx_wood_1", "sfx_wood_2", "sfx_wood_3"),
        "metal" to loadBank("sfx_metal_1", "sfx_metal_2", "sfx_metal_3"),
        "glass" to loadBank("sfx_glass_1", "sfx_glass_2", "sfx_glass_3"),
        "soft" to loadBank("sfx_soft_1", "sfx_soft_2", "sfx_soft_3"),
        "generic" to loadBank("sfx_generic_1", "sfx_generic_2"),
        "explosion" to loadBank("sfx_explosion_1", "sfx_metal_3", "sfx_generic_2"),
    )

    fun impact(intensity: Float, material: MaterialStyle?) {
        if (intensity < 1.8f) return
        val bank = when (material) {
            MaterialStyle.WOOD -> banks["wood"]
            MaterialStyle.STEEL, MaterialStyle.HEAVY -> banks["metal"]
            MaterialStyle.GLASS, MaterialStyle.ICE -> banks["glass"]
            MaterialStyle.RUBBER, MaterialStyle.JELLY -> banks["soft"]
            null -> banks["generic"]
        }.orEmpty()
        play(bank, intensity)
    }

    fun explosion(intensity: Float = 1f) = play(banks["explosion"].orEmpty(), 9f * intensity.coerceAtLeast(0.4f))
    fun close() = pool.release()

    private fun loadBank(vararg names: String): List<Int> = names.mapNotNull { name ->
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        if (id == 0) null else pool.load(context, id, 1).takeIf { it != 0 }
    }

    private fun play(bank: List<Int>, intensity: Float) {
        if (bank.isEmpty()) return
        val sample = bank[Random.nextInt(bank.size)]
        val volume = (0.12f + intensity * 0.055f).coerceIn(0.16f, 1f)
        val rate = 0.96f + Random.nextFloat() * 0.08f
        pool.play(sample, volume, volume, 1, 0, rate)
    }
}
