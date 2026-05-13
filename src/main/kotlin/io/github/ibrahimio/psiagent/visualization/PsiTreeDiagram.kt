package io.github.ibrahimio.psiagent.visualization

import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

/**
 * Simple top-down tree diagram renderer for PsiTreePresentationNode.
 * Computes a horizontal layout where parent is centered above its children.
 */
class PsiTreeDiagram : JComponent() {
    private var root: PsiTreePresentationNode? = null
    private val nodeBounds = mutableMapOf<PsiTreePresentationNode, Rectangle>()

    private val nodeWidth = 220
    private val nodeHeight = 56
    private val levelGap = 56
    private val siblingGap = 24

    fun setRoot(root: PsiTreePresentationNode?) {
        this.root = root
        relayout()
    }

    private fun relayout() {
        nodeBounds.clear()
        root ?: run {
            preferredSize = Dimension(400, 200)
            revalidate()
            repaint()
            return
        }

        // compute subtree widths
        fun computeWidth(n: PsiTreePresentationNode): Int {
            if (n.children.isEmpty()) return nodeWidth
            val widths = n.children.map { computeWidth(it) }
            return widths.sum() + siblingGap * (widths.size - 1)
        }

        fun layoutNode(n: PsiTreePresentationNode, x: Int, y: Int) {
            if (n.children.isEmpty()) {
                nodeBounds[n] = Rectangle(x, y, nodeWidth, nodeHeight)
                return
            }
            val totalWidth = computeWidth(n)
            var cx = x
            n.children.forEach { child ->
                val w = computeWidth(child)
                val childX = cx
                val childY = y + nodeHeight + levelGap
                layoutNode(child, childX, childY)
                cx += w + siblingGap
            }
            // center parent above children
            val first = n.children.first()
            val last = n.children.last()
            val left = nodeBounds[first]!!.x
            val right = nodeBounds[last]!!.x + nodeBounds[last]!!.width
            val px = left + (right - left - nodeWidth) / 2
            nodeBounds[n] = Rectangle(px, y, nodeWidth, nodeHeight)
        }

        val total = computeWidth(root!!)
        layoutNode(root!!, 20, 20)

        // compute preferred size
        val maxX = nodeBounds.values.maxOfOrNull { it.x + it.width } ?: 400
        val maxY = nodeBounds.values.maxOfOrNull { it.y + it.height } ?: 200
        preferredSize = Dimension(maxX + 40, maxY + 40)
        revalidate()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = background
        g2.fillRect(0, 0, width, height)

        val r = root ?: return

        // draw edges
        g2.stroke = BasicStroke(2f)
        g2.color = Color(0x999999)
        fun drawEdges(n: PsiTreePresentationNode) {
            val pr = nodeBounds[n] ?: return
            val px = pr.x + pr.width / 2
            val py = pr.y + pr.height
            n.children.forEach { c ->
                val cr = nodeBounds[c] ?: return@forEach
                val cx = cr.x + cr.width / 2
                val cy = cr.y
                g2.drawLine(px, py, cx, cy)
                drawEdges(c)
            }
        }
        drawEdges(r)

        // draw nodes
        for ((node, rect) in nodeBounds) {
            drawNode(g2, node, rect)
        }
    }

    private fun drawNode(g2: Graphics2D, node: PsiTreePresentationNode, rect: Rectangle) {
        val rr = RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), 12f, 12f)
        // fill
        g2.color = Color(0xEAF2FF)
        g2.fill(rr)
        // border
        g2.color = Color(0x2B6CB0)
        g2.stroke = BasicStroke(2f)
        g2.draw(rr)

        // text
        g2.color = Color.BLACK
        val fm = g2.fontMetrics
        val title = node.label
        val elided = if (fm.stringWidth(title) > rect.width - 16) {
            var s = title
            while (fm.stringWidth(s + "...") > rect.width - 16 && s.isNotEmpty()) s = s.dropLast(1)
            s + "..."
        } else title
        g2.drawString(elided, rect.x + 12, rect.y + 20 + fm.ascent - fm.descent)
    }
}

