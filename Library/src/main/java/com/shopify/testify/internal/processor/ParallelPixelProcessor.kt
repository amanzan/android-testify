package com.shopify.testify.internal.processor

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.IntBuffer
import java.util.BitSet
import kotlin.math.ceil

class ParallelPixelProcessor private constructor() {

    private var baselineBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null

    fun baseline(baselineBitmap: Bitmap): ParallelPixelProcessor {
        this.baselineBitmap = baselineBitmap
        return this
    }

    fun current(currentBitmap: Bitmap): ParallelPixelProcessor {
        this.currentBitmap = currentBitmap
        return this
    }

    private fun prepareBuffers(): ImageBuffers {
        val width = currentBitmap!!.width
        val height = currentBitmap!!.height

        return ImageBuffers(
            width = width,
            height = height,
            baselineBuffer = IntBuffer.allocate(width * height),
            currentBuffer = IntBuffer.allocate(width * height)
        ).apply {
            baselineBitmap!!.copyPixelsToBuffer(baselineBuffer)
            currentBitmap!!.copyPixelsToBuffer(currentBuffer)
            baselineBitmap = null
            currentBitmap = null
        }
    }

    private fun getChunkData(width: Int, height: Int): ChunkData {
        val size = width * height
        val chunkSize = size / numberOfCores
        val chunks = ceil(size.toFloat() / chunkSize.toFloat()).toInt()
        return ChunkData(size, chunks, chunkSize)
    }

    private fun runBlockingInChunks(chunkData: ChunkData, fn: CoroutineScope.(chunk: Int, index: Int) -> Boolean) {
        runBlocking {
            launch(executorDispatcher) {
                (0 until chunkData.chunks).map { chunk ->
                    async {
                        for (i in (chunk * chunkData.chunkSize) until ((chunk + 1) * chunkData.chunkSize)) {
                            if (!fn(chunk, i)) break
                        }
                    }
                }.awaitAll()
            }
        }
    }

    fun analyze(analyzer: (baselinePixel: Int, currentPixel: Int, position: Pair<Int, Int>) -> Boolean): Boolean {
        val (width, height, baselineBuffer, currentBuffer) = prepareBuffers()

        val chunkData = getChunkData(width, height)
        val results = BitSet(chunkData.chunks).apply { set(0, chunkData.chunks) }

        runBlockingInChunks(chunkData) { chunk, index ->

            val globalOffset = chunk * chunkData.chunkSize + index
            val x = globalOffset % width
            val y = globalOffset / height

            if (!analyzer(baselineBuffer[index], currentBuffer[index], Pair(x, y))) {
                results.clear(chunk)
                false
            } else {
                true
            }
        }
        return results.cardinality() == chunkData.chunks
    }

    fun transform(transformer: (baselinePixel: Int, currentPixel: Int, position: Pair<Int, Int>) -> Int): TransformResult {
        val (width, height, baselineBuffer, currentBuffer) = prepareBuffers()

        val chunkData = getChunkData(width, height)
        val diffBuffer = IntBuffer.allocate(chunkData.size)

        runBlockingInChunks(chunkData) { chunk, index ->

            val globalOffset = chunk * chunkData.chunkSize + index
            val x = globalOffset % width
            val y = globalOffset / height

            diffBuffer.put(index, transformer(baselineBuffer[index], currentBuffer[index], Pair(x, y)))
            true
        }

        return TransformResult(
            width,
            height,
            diffBuffer.array()
        )
    }

    private data class ChunkData(
        val size: Int,
        val chunks: Int,
        val chunkSize: Int
    )

    private data class ImageBuffers(
        val width: Int,
        val height: Int,
        val baselineBuffer: IntBuffer,
        val currentBuffer: IntBuffer
    )

    @Suppress("ArrayInDataClass")
    data class TransformResult(
        val width: Int,
        val height: Int,
        val pixels: IntArray
    )

    companion object {
        fun create(): ParallelPixelProcessor {
            return ParallelPixelProcessor()
        }
    }
}
