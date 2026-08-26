package ru.vibecut.editor

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    @Suppress("DEPRECATION")
    fun start(): File {
        stopSafely(true)
        val file = File(File(context.filesDir, "voiceovers").apply { mkdirs() }, "voice_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare(); start()
        }
        recorder = mediaRecorder; outputFile = file
        return file
    }

    fun stop(): File? {
        val file = outputFile
        return try { recorder?.stop(); recorder?.release(); recorder=null; outputFile=null; file?.takeIf { it.exists() && it.length()>0L } }
        catch (_: Throwable) { runCatching { recorder?.release() }; recorder=null; outputFile=null; file?.delete(); null }
    }

    fun cancel() { stopSafely(true)?.delete() }

    private fun stopSafely(deleteIfBroken:Boolean):File? { if(recorder==null)return null;val file=outputFile;return try{recorder?.stop();recorder?.release();recorder=null;outputFile=null;file}catch(_:Throwable){runCatching{recorder?.release()};recorder=null;outputFile=null;if(deleteIfBroken)file?.delete();null} }
}
