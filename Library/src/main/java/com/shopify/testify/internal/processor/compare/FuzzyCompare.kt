/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Shopify Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.shopify.testify.internal.processor.compare

import android.graphics.Bitmap
import androidx.annotation.ColorInt
import com.github.ajalt.colormath.RGB
import com.shopify.testify.internal.processor.ParallelPixelProcessor
import com.shopify.testify.internal.processor.compare.colorspace.calculateDeltaE

internal class FuzzyCompare(private val exactness: Float) : BitmapCompare {

    override fun compareBitmaps(baselineBitmap: Bitmap, currentBitmap: Bitmap): Boolean {
        if (baselineBitmap.height != currentBitmap.height) {
            return false
        }

        if (baselineBitmap.width != currentBitmap.width) {
            return false
        }

        return ParallelPixelProcessor
            .create()
            .baseline(baselineBitmap)
            .current(currentBitmap)
            .analyze { baselinePixel, currentPixel ->
                if (baselinePixel == currentPixel) {
                    /* return  */ true
                } else {
                    val baselineLab = RGB.fromInt(baselinePixel).toLAB()
                    val currentLab = RGB.fromInt(currentPixel).toLAB()

                    val deltaE = calculateDeltaE(
                        baselineLab.l,
                        baselineLab.a,
                        baselineLab.b,
                        currentLab.l,
                        currentLab.a,
                        currentLab.b
                    )
                    /* return  */ ((100.0 - deltaE) / 100.0f >= exactness)
                }
            }
    }
}

internal class OldFuzzyCompare(private val exactness: Float) : BitmapCompare {

    override fun compareBitmaps(baselineBitmap: Bitmap, currentBitmap: Bitmap): Boolean {
        if (baselineBitmap.height != currentBitmap.height) {
            return false
        }

        if (baselineBitmap.width != currentBitmap.width) {
            return false
        }

        val height = baselineBitmap.height
        val width = baselineBitmap.width

        for (y in 0 until height) {
            x@ for (x in 0 until width) {
                @ColorInt val baselineColor = baselineBitmap.getPixel(x, y)
                @ColorInt val currentColor = currentBitmap.getPixel(x, y)

                if (baselineColor == currentColor) continue@x

                val baselineLab = RGB.fromInt(baselineColor).toLAB()
                val currentLab = RGB.fromInt(currentColor).toLAB()

                val deltaE = calculateDeltaE(
                    baselineLab.l,
                    baselineLab.a,
                    baselineLab.b,
                    currentLab.l,
                    currentLab.a,
                    currentLab.b
                )
                if ((100.0 - deltaE) / 100.0f < exactness) {
                    return false
                }
            }
        }
        return true
    }
}

internal class ParallelFuzzyCompare(private val exactness: Float) : BitmapCompare {

    override fun compareBitmaps(baselineBitmap: Bitmap, currentBitmap: Bitmap): Boolean {
        if (baselineBitmap.height != currentBitmap.height) {
            return false
        }

        if (baselineBitmap.width != currentBitmap.width) {
            return false
        }

        if (baselineBitmap.sameAs(currentBitmap)) return true

        return ParallelPixelProcessor
            .create()
            .baseline(baselineBitmap)
            .current(currentBitmap)
            .analyze { baselinePixel, currentPixel ->
                if (baselinePixel == currentPixel) {
                    /* return  */ true
                } else {
                    val baselineLab = RGB.fromInt(baselinePixel).toLAB()
                    val currentLab = RGB.fromInt(currentPixel).toLAB()

                    val deltaE = calculateDeltaE(
                        baselineLab.l,
                        baselineLab.a,
                        baselineLab.b,
                        currentLab.l,
                        currentLab.a,
                        currentLab.b
                    )
                    /* return  */ ((100.0 - deltaE) / 100.0f >= exactness)
                }
            }
    }
}
