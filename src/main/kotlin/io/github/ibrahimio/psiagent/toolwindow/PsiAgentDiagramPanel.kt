package io.github.ibrahimio.psiagent.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.SwingUtilities
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class PsiAgentDiagramPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val statusLabel = JBLabel("Agent: unknown")
    private val checkButton = JButton("Check")
    private val searchField = JBTextField()
    private val searchButton = JButton("Search")
    private val treeButton = JButton("Tree")
    private val resetLayoutButton = JButton("Reset layout")
    private val canvas = DiagramCanvas()

    init {
        val top = JPanel(FlowLayout(FlowLayout.LEFT))
        top.add(statusLabel)
        top.add(checkButton)
        top.add(searchField)
        searchField.columns = 30
        top.add(searchButton)
        top.add(treeButton)
        top.add(resetLayoutButton)
        add(top, BorderLayout.NORTH)

        val scroll = JBScrollPane(canvas)
        add(scroll, BorderLayout.CENTER)

        checkButton.addActionListener { updateStatus() }
        searchButton.addActionListener { doSearch() }
        treeButton.addActionListener { canvas.arrangeAsTree() }
        resetLayoutButton.addActionListener { canvas.resetLayout() }

        // initial status
        updateStatus()
    }

    private fun updateStatus() {
        Thread {
            val ok = PsiAgentClient.ping()
            SwingUtilities.invokeLater {
                statusLabel.text = if (ok) "Agent: online" else "Agent: offline"
            }
        }.start()
    }

    private fun doSearch() {
        val q = searchField.text.trim()
        if (q.isEmpty()) return
        statusLabel.text = "Searching..."
        Thread {
            val results = PsiAgentClient.search(q)
            SwingUtilities.invokeLater {
                statusLabel.text = "Found ${results.size}"
                canvas.updateNodes(results)
            }
        }.start()
    }

    override fun dispose() {
        // nothing to dispose explicitly
    }

    // Simple drawable diagram canvas with draggable nodes
    private class DiagramCanvas : JComponent() {
        private val nodes = mutableListOf<DiagramNode>()
        private var dragNode: DiagramNode? = null
        private var dragOffsetX = 0
        private var dragOffsetY = 0
        private var originalPositions: List<Pair<Int, Int>> = emptyList()

        init {
            preferredSize = Dimension(1200, 800)
            background = Color.WHITE
            isOpaque = true

            val ma = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val p = e.point
                    dragNode = nodes.findLast { it.bounds().contains(p) }
                    if (dragNode != null) {
                        dragOffsetX = p.x - dragNode!!.x
                        dragOffsetY = p.y - dragNode!!.y
                    }
                    repaint()
                }

                override fun mouseReleased(e: MouseEvent) {
                    dragNode = null
                    repaint()
                }

                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val p = e.point
                        val node = nodes.findLast { it.bounds().contains(p) }
                        if (node != null) {
                            // try to open file if present
                            node.file?.let { path ->
                                // best-effort: open file in IDE using com.intellij.openapi.fileEditor
                                // We avoid heavy API calls here; leaving placeholder
                            }
                        }
                    }
                }
            }

            addMouseListener(ma)
            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseDragged(e: MouseEvent) {
                    val dn = dragNode ?: return
                    dn.x = e.x - dragOffsetX
                    dn.y = e.y - dragOffsetY
                    revalidate()
                    repaint()
                }
            })
        }

        fun updateNodes(results: List<SearchResult>) {
            nodes.clear()
            // layout nodes in a grid
            val cols = 4
            val marginX = 40
            val marginY = 40
            val cellW = 240
            val cellH = 100
            results.forEachIndexed { i, r ->
                val col = i % cols
                val row = i / cols
                val x = marginX + col * (cellW + marginX)
                val y = marginY + row * (cellH + marginY)
                nodes.add(DiagramNode("n$i", x, y, r.name, r.file))
            }
            // store original positions for reset
            originalPositions = nodes.map { it.x to it.y }
            // expand preferred size if needed
            val w = (cols * (cellW + marginX) + marginX).coerceAtLeast(800)
            val rows = (results.size + cols - 1) / cols
            val h = (rows * (cellH + marginY) + marginY).coerceAtLeast(600)
            preferredSize = Dimension(w, h)
            revalidate()
            repaint()
        }

        fun resetLayout() {
            if (originalPositions.isEmpty()) return
            nodes.forEachIndexed { i, node ->
                val (ox, oy) = originalPositions.getOrNull(i) ?: (node.x to node.y)
                node.x = ox
                node.y = oy
            }
            revalidate()
            repaint()
        }

        /**
         * Arrange nodes as a simple binary tree for visualization.
         * Parent of node i is (i-1)/2. This creates a predictable top-down tree layout.
         */
        fun arrangeAsTree() {
            if (nodes.isEmpty()) return
            val levelSpacing = 140
            val siblingSpacing = 40
            // compute levels
            val levels = mutableListOf<MutableList<DiagramNode>>()
            nodes.forEachIndexed { i, node ->
                var level = 0
                var idx = i
                while (idx > 0) {
                    idx = (idx - 1) / 2
                    level++
                }
                while (levels.size <= level) levels.add(mutableListOf())
                levels[level].add(node)
            }

            // compute width per level and assign x positions centered
            val canvasWidth = if (preferredSize.width > 800) preferredSize.width else 800
            var y = 40
            levels.forEach { levelNodes ->
                val totalWidth = levelNodes.size * 200 + (levelNodes.size - 1) * siblingSpacing
                var x = (canvasWidth - totalWidth) / 2
                levelNodes.forEach { n ->
                    n.x = x
                    n.y = y
                    x += 200 + siblingSpacing
                }
                y += levelSpacing
            }

            // update preferred size if tree is tall/wide
            var maxX = preferredSize.width
            var maxY = preferredSize.height
            for (n in nodes) {
                val nx = n.x + 220
                val ny = n.y + 100
                if (nx > maxX) maxX = nx
                if (ny > maxY) maxY = ny
            }
            val newW = if (maxX > preferredSize.width) maxX else preferredSize.width
            val newH = if (maxY > preferredSize.height) maxY else preferredSize.height
            preferredSize = Dimension(newW, newH)
            revalidate()
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            // anti-alias
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            // background
            g2.color = background
            g2.fillRect(0, 0, width, height)

            // draw edges (none for now)

            // draw nodes
            nodes.forEach { node ->
                drawNode(g2, node)
            }
        }

        private fun drawNode(g2: Graphics2D, node: DiagramNode) {
            val padding = 12
            val w = 200
            val h = 64
            val rx = 12f
            val r = RoundRectangle2D.Float(node.x.toFloat(), node.y.toFloat(), w.toFloat(), h.toFloat(), rx, rx)

            // fill
            g2.color = Color(0xE6F2FF)
            g2.fill(r)

            // border
            g2.color = Color(0x2B6CB0)
            g2.stroke = BasicStroke(2f)
            g2.draw(r)

            // text
            g2.color = Color.BLACK
            val fm = g2.fontMetrics
            val title = node.label
            val elided = if (fm.stringWidth(title) > (w - padding * 2)) {
                var s = title
                while (fm.stringWidth(s + "...") > (w - padding * 2) && s.isNotEmpty()) s = s.dropLast(1)
                s + "..."
            } else title
            g2.drawString(elided, node.x + padding, node.y + padding + fm.ascent)

            // filename smaller
            node.file?.let { f ->
                g2.font = g2.font.deriveFont(Font.PLAIN, g2.font.size - 2f)
                g2.color = Color.DARK_GRAY
                val short = f.substringAfterLast('/').substringAfterLast('\\')
                g2.drawString(short, node.x + padding, node.y + padding + fm.ascent + 20)
            }
        }
    }

    private data class DiagramNode(val id: String, var x: Int, var y: Int, val label: String, val file: String?) {
        fun bounds(): Rectangle = Rectangle(x, y, 200, 64)
    }
}





