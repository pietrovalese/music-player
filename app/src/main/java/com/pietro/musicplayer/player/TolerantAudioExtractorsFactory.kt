package com.pietro.musicplayer.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractorInput
import androidx.media3.extractor.MpegAudioUtil
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import kotlin.math.min

/**
 * [ExtractorsFactory] configured for maximum tolerance on malformed local audio files:
 * enables constant-bitrate seeking fallback, ignores broken ID3 metadata when needed, and
 * falls back to [ResilientMp3Extractor] for MP3s with leading garbage bytes before the first
 * valid MPEG sync frame.
 */
@OptIn(UnstableApi::class)
class TolerantAudioExtractorsFactory : ExtractorsFactory {

    private val defaultFactory = DefaultExtractorsFactory()
        .setConstantBitrateSeekingEnabled(true)
        .setConstantBitrateSeekingAlwaysEnabled(true)
        .setMp3ExtractorFlags(
            Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS or
                Mp3Extractor.FLAG_DISABLE_ID3_METADATA
        )

    override fun createExtractors(): Array<Extractor> =
        defaultFactory.createExtractors() + ResilientMp3Extractor()

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>
    ): Array<Extractor> =
        defaultFactory.createExtractors(uri, responseHeaders) + ResilientMp3Extractor()
}

/**
 * Fallback MP3 extractor that scans up to 512 KB for two consecutive valid MPEG audio frames,
 * allowing playback of MP3 files with leading junk bytes, truncated/corrupt ID3v2 headers, or
 * non-standard preambles that reject [Mp3Extractor.sniff].
 */
@OptIn(UnstableApi::class)
private class ResilientMp3Extractor : Extractor {

    private val delegate = Mp3Extractor(
        Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
            Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS or
            Mp3Extractor.FLAG_DISABLE_ID3_METADATA
    )

    private var firstFrameOffset: Long = -1L
    private var skippedInitialOffset: Boolean = false

    override fun sniff(input: ExtractorInput): Boolean {
        return try {
            val offset = findFirstMpegFrameOffset(input)
            firstFrameOffset = offset
            offset >= 0L
        } finally {
            input.resetPeekPosition()
        }
    }

    override fun init(output: ExtractorOutput) {
        val wrappedOutput = if (firstFrameOffset > 0L) {
            OffsetExtractorOutput(output, firstFrameOffset)
        } else {
            output
        }
        delegate.init(wrappedOutput)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val offset = firstFrameOffset.coerceAtLeast(0L)
        if (!skippedInitialOffset && offset > 0L) {
            val remainingToSkip = offset - input.position
            if (remainingToSkip > 0L) {
                input.skipFully(remainingToSkip.toInt())
            }
            skippedInitialOffset = true
        }
        val wrappedInput = if (offset > 0L) OffsetExtractorInput(input, offset) else input
        val result = delegate.read(wrappedInput, seekPosition)
        if (result == Extractor.RESULT_SEEK && offset > 0L) {
            seekPosition.position += offset
        }
        return result
    }

    override fun seek(position: Long, timeUs: Long) {
        val offset = firstFrameOffset.coerceAtLeast(0L)
        if (position < offset) {
            skippedInitialOffset = false
            delegate.seek(0L, timeUs)
        } else {
            delegate.seek(position - offset, timeUs)
        }
    }

    override fun release() {
        delegate.release()
    }

    private fun findFirstMpegFrameOffset(input: ExtractorInput): Long {
        val streamLength = input.length
        val maxScan = if (streamLength != C.LENGTH_UNSET.toLong()) {
            min(MAX_SNIFF_BYTES.toLong(), streamLength).toInt()
        } else {
            MAX_SNIFF_BYTES
        }
        if (maxScan < 8) return -1L

        val buffer = ByteArray(maxScan)
        var totalPeeked = 0
        while (totalPeeked < maxScan) {
            val peeked = input.peek(buffer, totalPeeked, maxScan - totalPeeked)
            if (peeked == C.RESULT_END_OF_INPUT) break
            totalPeeked += peeked
        }

        if (totalPeeked < 8) return -1L

        val limit = totalPeeked - 4
        var i = 0
        while (i <= limit) {
            if ((buffer[i].toInt() and 0xFF) == 0xFF && (buffer[i + 1].toInt() and 0xE0) == 0xE0) {
                val header1 = readBigEndianInt(buffer, i)
                val frameSize1 = MpegAudioUtil.getFrameSize(header1)
                if (frameSize1 != C.LENGTH_UNSET && frameSize1 > 0) {
                    val nextOffset = i + frameSize1
                    if (nextOffset + 4 <= totalPeeked) {
                        val header2 = readBigEndianInt(buffer, nextOffset)
                        val frameSize2 = MpegAudioUtil.getFrameSize(header2)
                        val sameFormat = (header1 and MPEG_HEADER_MATCH_MASK) == (header2 and MPEG_HEADER_MATCH_MASK)
                        if (frameSize2 != C.LENGTH_UNSET && frameSize2 > 0 && sameFormat) {
                            return i.toLong()
                        }
                    }
                }
            }
            i++
        }
        return -1L
    }

    private fun readBigEndianInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    private class OffsetExtractorInput(
        input: ExtractorInput,
        private val offset: Long
    ) : ForwardingExtractorInput(input) {

        override fun getPeekPosition(): Long = (super.getPeekPosition() - offset).coerceAtLeast(0L)

        override fun getPosition(): Long = (super.getPosition() - offset).coerceAtLeast(0L)

        override fun getLength(): Long {
            val len = super.getLength()
            return if (len == C.LENGTH_UNSET.toLong()) len else (len - offset).coerceAtLeast(0L)
        }

        override fun <E : Throwable> setRetryPosition(position: Long, e: E) {
            super.setRetryPosition(position + offset, e)
        }
    }

    private class OffsetExtractorOutput(
        private val delegate: ExtractorOutput,
        private val offset: Long
    ) : ExtractorOutput {

        override fun track(id: Int, type: Int): TrackOutput = delegate.track(id, type)

        override fun endTracks() = delegate.endTracks()

        override fun seekMap(seekMap: SeekMap) {
            delegate.seekMap(object : SeekMap {
                override fun isSeekable(): Boolean = seekMap.isSeekable

                override fun getDurationUs(): Long = seekMap.durationUs

                override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
                    val points = seekMap.getSeekPoints(timeUs)
                    val first = SeekPoint(points.first.timeUs, points.first.position + offset)
                    val second = SeekPoint(points.second.timeUs, points.second.position + offset)
                    return SeekMap.SeekPoints(first, second)
                }
            })
        }
    }

    companion object {
        private const val MAX_SNIFF_BYTES = 512 * 1024
        private const val MPEG_HEADER_MATCH_MASK = 0xFFFE0C00.toInt()
    }
}
