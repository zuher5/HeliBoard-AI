/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioUtils {
    /**
     * Packages raw 16-bit PCM audio samples into a standard 44-byte RIFF/WAV format byte array.
     *
     * @param pcmData Raw PCM audio byte array.
     * @param sampleRate Sampling rate in Hz (default 16000).
     * @param channels Number of audio channels (default 1 for Mono).
     * @param bitsPerSample Bit depth per sample (default 16).
     */
    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF/WAVE header
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt((totalDataLen and 0xffffffffL).toInt())
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // 'fmt ' chunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1.toShort()) // AudioFormat 1 = PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt((byteRate and 0xffffffffL).toInt())
        buffer.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
        buffer.putShort(bitsPerSample.toShort())

        // 'data' chunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt((totalAudioLen and 0xffffffffL).toInt())

        val wavStream = ByteArrayOutputStream(44 + pcmData.size)
        wavStream.write(header)
        wavStream.write(pcmData)
        return wavStream.toByteArray()
    }
}
