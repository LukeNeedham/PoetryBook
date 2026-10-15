package com.lukeneedham.poetrybook.pageturn

import com.lukeneedham.poetrybook.pageturn.geometry.PageCurlGeometry
import com.lukeneedham.poetrybook.pageturn.geometry.PageCurlGeometryEngine
import com.lukeneedham.poetrybook.pageturn.geometry.Point
import com.lukeneedham.poetrybook.pageturn.geometry.Polygon
import com.lukeneedham.poetrybook.pageturn.geometry.Rect
import com.lukeneedham.poetrybook.pageturn.geometry.calculateReturningPage
import com.lukeneedham.poetrybook.pageturn.turn.CurlOrigin
import com.lukeneedham.poetrybook.pageturn.turn.TurnDirection
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Test

/** Renders the fold of a few simulated turns to PNGs (build/curl-frames) so the motion can be looked at. */
class CurlFramesRenderTest {
    private val w = 360.0
    private val h = 640.0
    private val bounds = Rect(0.0, 0.0, w, h)
    private val engine = PageCurlGeometryEngine()

    private fun path(p: Polygon) = Path2D.Double().also { path ->
        if (p.points.isEmpty()) return@also
        p.points.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }
        path.closePath()
    }

    private fun sheet(name: String, frames: List<Pair<String, PageCurlGeometry>>) {
        val cols = 6
        val rows = (frames.size + cols - 1) / cols
        val cell = 0.5
        val cw = (w * cell).toInt() + 16
        val ch = (h * cell).toInt() + 30
        val img = BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(40, 40, 40); g.fillRect(0, 0, img.width, img.height)
        frames.forEachIndexed { i, (label, geo) ->
            val ox = (i % cols) * cw + 8
            val oy = (i / cols) * ch + 20
            g.color = Color.WHITE; g.drawString(label, ox, oy - 6)
            g.translate(ox.toDouble(), oy.toDouble()); g.scale(cell, cell)
            g.clip = java.awt.geom.Rectangle2D.Double(0.0, 0.0, w, h)
            g.color = Color(120, 170, 120); g.fill(java.awt.geom.Rectangle2D.Double(0.0, 0.0, w, h)) // page underneath
            g.color = Color(235, 225, 200); g.fill(path(geo.flatFront))                              // flat part of moving page
            g.color = Color(210, 150, 90, 230); g.fill(path(geo.reflectedBack))                      // lifted flap
            g.color = Color.BLACK; g.stroke = java.awt.BasicStroke(2f); g.draw(path(geo.reflectedBack))
            g.clip = null; g.transform = java.awt.geom.AffineTransform()
        }
        g.dispose()
        val dir = File("build/curl-frames").apply { mkdirs() }
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    private fun lerp(a: Point, b: Point, t: Double) = Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    @Test
    fun forwardDragThenSettle() {
        val anchorY = 400.0
        val frames = mutableListOf<Pair<String, PageCurlGeometry>>()
        // finger drags left 180px (half the page), then releases and settles to the completion point
        val steps = listOf(0.0, 20.0, 60.0, 120.0, 180.0)
        steps.forEach { dx ->
            val g = engine.calculate(bounds, Point(w - dx, anchorY), TurnDirection.FORWARD, CurlOrigin.OUTER_EDGE, anchorY)
            frames += "drag ${dx.toInt()} p=${"%.2f".format(g.progress)}" to g
        }
        val start = Point(w - 180.0, anchorY); val end = Point(-w, anchorY)
        listOf(0.15, 0.3, 0.45, 0.6, 0.75, 0.9, 1.0).forEach { t ->
            val g = engine.calculate(bounds, lerp(start, end, t), TurnDirection.FORWARD, CurlOrigin.OUTER_EDGE, anchorY)
            frames += "settle ${"%.2f".format(t)} p=${"%.2f".format(g.progress)}" to g
        }
        sheet("forward-drag", frames)
        assertTrue(frames.all { it.second.isFinite })
    }

    @Test
    fun forwardTapTurn() {
        val grab = Point(w - 1.5, h - 1.5)
        val end = Point(-w, h)
        val frames = (0..11).map { i ->
            val t = i / 11.0
            val g = engine.calculate(bounds, lerp(grab, end, t), TurnDirection.FORWARD, CurlOrigin.LOWER_OUTER, null)
            "tap ${"%.2f".format(t)} p=${"%.2f".format(g.progress)}" to g
        }
        sheet("forward-tap", frames)
        assertTrue(frames.all { it.second.isFinite })
    }

    @Test
    fun backwardDragThenSettle() {
        val anchorY = 400.0
        val frames = mutableListOf<Pair<String, PageCurlGeometry>>()
        listOf(0.0, 20.0, 60.0, 120.0, 180.0).forEach { dx ->
            val g = engine.calculateReturningPage(bounds, Point(dx, anchorY), CurlOrigin.OUTER_EDGE, anchorY)
            frames += "drag ${dx.toInt()} p=${"%.2f".format(g.progress)}" to g
        }
        val start = Point(180.0, anchorY); val end = Point(2 * w, anchorY)
        listOf(0.15, 0.3, 0.45, 0.6, 0.75, 0.9, 1.0).forEach { t ->
            val g = engine.calculateReturningPage(bounds, lerp(start, end, t), CurlOrigin.OUTER_EDGE, anchorY)
            frames += "settle ${"%.2f".format(t)} p=${"%.2f".format(g.progress)}" to g
        }
        sheet("backward-drag", frames)
        assertTrue(frames.all { it.second.isFinite })
    }
}
