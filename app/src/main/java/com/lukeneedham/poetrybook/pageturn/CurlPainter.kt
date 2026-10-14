package com.lukeneedham.poetrybook.pageturn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import com.lukeneedham.poetrybook.pageturn.geometry.AffineTransform2D
import com.lukeneedham.poetrybook.pageturn.geometry.PageCurlBandGeometry
import com.lukeneedham.poetrybook.pageturn.geometry.PageCurlBandGeometryEngine
import com.lukeneedham.poetrybook.pageturn.geometry.PageCurlGeometry
import com.lukeneedham.poetrybook.pageturn.geometry.PaperCurlLighting
import com.lukeneedham.poetrybook.pageturn.geometry.Point
import com.lukeneedham.poetrybook.pageturn.geometry.Polygon
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Paints the lifted, reflected back of the turning sheet and the shading around the fold.
 *
 * The flat part of the sheet and the sheet underneath are drawn by the caller; this draws
 * everything from the cast shadow onwards. [back] holds the content that appears on the reverse
 * of the lifted paper, already laid out at its final position.
 *
 * Ported from TurnThePage's physical book painter, trimmed to a single paper type.
 */
internal fun DrawScope.drawCurl(
    geometry: PageCurlGeometry,
    bounds: Rect,
    back: GraphicsLayer,
    bandEngine: PageCurlBandGeometryEngine,
) {
    if (geometry.reflectedBack.points.size < 3) return

    val backPath = geometry.reflectedBack.toPath()
    val foldStart = geometry.fold.start.toOffset()
    val foldEnd = geometry.fold.end.toOffset()
    val normal = geometry.fold.normal
    val lift = geometry.lift.toFloat()
    val pageWidth = bounds.width
    val midpoint = Offset((foldStart.x + foldEnd.x) * 0.5f, (foldStart.y + foldEnd.y) * 0.5f)
    val normalOffset = normal.toOffset()
    val lighting = PaperCurlLighting.calculate(pageWidth.toDouble(), density.toDouble(), geometry.lift)

    // The cast shadow falls onto the flat sheet. It is broad, neutral and independent from the
    // contact shadow drawn at the actual paper-to-paper boundary below.
    val castWidth = lighting.castWidth.toFloat()
    val castOpacity = lighting.castOpacity.toFloat()
    clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
        drawFoldBand(
            foldStart = foldStart,
            foldEnd = foldEnd,
            normal = normal,
            negativeWidth = 0.75f,
            positiveWidth = castWidth,
            brush = Brush.linearGradient(
                0f to Color.Black.copy(alpha = castOpacity),
                0.14f to Color.Black.copy(alpha = castOpacity * .66f),
                0.52f to Color.Black.copy(alpha = castOpacity * .24f),
                1f to Color.Transparent,
                start = midpoint,
                end = midpoint + normalOffset * castWidth,
            ),
        )
    }

    val curlBand = bandEngine.calculate(
        geometry = geometry,
        stripCount = 12,
        minimumWidth = max(density * 10f, pageWidth * 0.12f).toDouble(),
    )
    if (curlBand.isActive) {
        drawCurvedSheet(back, curlBand)
    } else {
        drawSheetPiece(
            back = back,
            clip = geometry.reflectedBack,
            destinationToDevice = geometry.destinationToReflectedBack,
        )
    }

    // A single continuous lighting field sits above all affine pieces. It hides tiny raster seams
    // while preserving the readable content that is already attached to the moving paper.
    val shadedDepth = max(28f, pageWidth * 0.78f)
    drawPath(
        path = backPath,
        brush = Brush.linearGradient(
            0f to Color.Black.copy(alpha = lighting.creaseOpacity.toFloat()),
            0.07f to Color.White.copy(alpha = lighting.ridgeOpacity.toFloat() * .7f),
            0.24f to Color.Transparent,
            0.78f to Color.Transparent,
            1f to Color.Black.copy(alpha = .012f * lift),
            start = midpoint,
            end = midpoint - normalOffset * shadedDepth,
        ),
    )

    drawPath(
        path = backPath,
        color = Color.Black.copy(alpha = .12f * lift),
        style = Stroke(width = 0.58f),
    )

    // A narrow neutral contact shadow joins the two surfaces.
    val contactNegativeWidth = lighting.contactWidth.toFloat()
    val contactPositiveWidth = contactNegativeWidth * .7f
    val contactOpacity = lighting.contactOpacity.toFloat()
    clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
        drawFoldBand(
            foldStart = foldStart,
            foldEnd = foldEnd,
            normal = normal,
            negativeWidth = contactNegativeWidth,
            positiveWidth = contactPositiveWidth,
            brush = Brush.linearGradient(
                0f to Color.Transparent,
                0.34f to Color.Black.copy(alpha = contactOpacity * .43f),
                0.62f to Color.Black.copy(alpha = contactOpacity),
                0.82f to Color.Black.copy(alpha = contactOpacity * .41f),
                1f to Color.Transparent,
                start = midpoint - normalOffset * contactNegativeWidth,
                end = midpoint + normalOffset * contactPositiveWidth,
            ),
        )
    }

    val ridge = Path().apply {
        val middle = midpoint - normalOffset * (3f * lift)
        moveTo(foldStart.x, foldStart.y)
        quadraticTo(middle.x, middle.y, foldEnd.x, foldEnd.y)
    }
    drawPath(
        ridge,
        Color.White.copy(alpha = lighting.ridgeOpacity.toFloat()),
        style = Stroke(width = 0.7f + 0.45f * lift, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawCurvedSheet(
    back: GraphicsLayer,
    curlBand: PageCurlBandGeometry,
) {
    curlBand.rigidRemainder?.takeIf { it.deformed.points.size >= 3 }?.let { remainder ->
        drawSheetPiece(
            back = back,
            clip = remainder.deformed,
            destinationToDevice = remainder.destinationToDeformed,
        )
    }

    // Draw lower pseudo-depth first. Where the 2D projection folds over itself, the crest then
    // naturally covers the sheet below instead of exposing the former hard-reflection ghost.
    curlBand.strips
        .asSequence()
        .filter { it.deformed.points.size >= 3 }
        .sortedBy { strip ->
            val u = ((strip.normalStart + strip.normalEnd) * 0.5 / curlBand.width)
                .coerceIn(0.0, 1.0)
            sin(PI * u) * curlBand.lift
        }
        .forEach { strip ->
            drawSheetPiece(
                back = back,
                clip = strip.deformed,
                destinationToDevice = strip.destinationToDeformed,
            )

            val u = ((strip.normalStart + strip.normalEnd) * 0.5 / curlBand.width)
                .toFloat()
                .coerceIn(0f, 1f)
            val crest = sin(PI.toFloat() * u) * curlBand.lift.toFloat()
            drawPath(
                path = strip.deformed.toPath(),
                color = if (u < 0.18f) {
                    Color.Black.copy(alpha = (0.18f - u) * 0.16f * curlBand.lift.toFloat())
                } else {
                    Color.White.copy(alpha = 0.030f * crest)
                },
            )
        }
}

private fun DrawScope.drawSheetPiece(
    back: GraphicsLayer,
    clip: Polygon,
    destinationToDevice: AffineTransform2D,
) {
    if (clip.points.size < 3) return
    clipPath(clip.toPath()) {
        withTransform({ transform(destinationToDevice.toComposeMatrix()) }) {
            drawLayer(back)
        }
    }
}

private fun DrawScope.drawFoldBand(
    foldStart: Offset,
    foldEnd: Offset,
    normal: Point,
    negativeWidth: Float,
    positiveWidth: Float,
    brush: Brush,
) {
    if (foldStart == foldEnd) return
    val n = normal.toOffset()
    val path = Path().apply {
        moveTo(foldStart.x - n.x * negativeWidth, foldStart.y - n.y * negativeWidth)
        lineTo(foldEnd.x - n.x * negativeWidth, foldEnd.y - n.y * negativeWidth)
        lineTo(foldEnd.x + n.x * positiveWidth, foldEnd.y + n.y * positiveWidth)
        lineTo(foldStart.x + n.x * positiveWidth, foldStart.y + n.y * positiveWidth)
        close()
    }
    drawPath(path, brush)
}

internal fun Polygon.toPath(): Path = Path().apply {
    points.firstOrNull()?.let { first ->
        moveTo(first.x.toFloat(), first.y.toFloat())
        points.drop(1).forEach { point -> lineTo(point.x.toFloat(), point.y.toFloat()) }
        close()
    }
}

private fun Point.toOffset(): Offset = Offset(x.toFloat(), y.toFloat())

private operator fun Offset.times(scale: Float): Offset = Offset(x * scale, y * scale)

/**
 * Converts the geometry module's row-equation affine transform to Compose's column-addressed
 * [Matrix] convention. Compose maps `x' = m[0,0]x + m[1,0]y + m[3,0]` and the equivalent y row.
 */
private fun AffineTransform2D.toComposeMatrix(): Matrix = Matrix().also { matrix ->
    matrix[0, 0] = m00.toFloat()
    matrix[1, 0] = m01.toFloat()
    matrix[3, 0] = m02.toFloat()
    matrix[0, 1] = m10.toFloat()
    matrix[1, 1] = m11.toFloat()
    matrix[3, 1] = m12.toFloat()
}
