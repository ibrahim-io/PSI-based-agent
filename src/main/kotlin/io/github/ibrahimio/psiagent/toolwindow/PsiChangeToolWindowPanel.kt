package io.github.ibrahimio.psiagent.toolwindow

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBList
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import io.github.ibrahimio.psiagent.visualization.PsiChangeListener
import io.github.ibrahimio.psiagent.visualization.PsiChangeRecord
import io.github.ibrahimio.psiagent.visualization.PsiDiffService
import io.github.ibrahimio.psiagent.visualization.PsiNodeChangeStatus
import io.github.ibrahimio.psiagent.visualization.PsiSnapshotNode
import io.github.ibrahimio.psiagent.visualization.PsiTreePresentationNode
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.ActionEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class PsiChangeToolWindowPanel(
    private val project: Project,
    private val service: PsiDiffService
) : BorderLayoutPanel(), Disposable {

    private val titleLabel = JBLabel("PSI change preview").apply {
        font = JBFont.h4()
    }
    private val summaryLabel = JBLabel("Run a PSI Agent refactoring to see a compact preview here.")
    private val viewSelectorLabel = JBLabel("View")
    private val viewSelector = JBList(View.entries.toList()).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = View.entries.size
        selectedIndex = 0
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? View)?.label ?: value?.toString().orEmpty()
                return component
            }
        }
    }
    // Diagram component to render the PSI tree as nodes and connections
    private val diagram = io.github.ibrahimio.psiagent.visualization.PsiTreeDiagram()
    private val asciiTreeLabel = JBLabel("Tree diagram")
    private val asciiTreeArea = JBTextArea().apply {
        isEditable = false
        lineWrap = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        text = "No PSI changes yet."
    }
    private val diffButton = JButton(object : AbstractAction("Show text diff") {
        override fun actionPerformed(e: ActionEvent?) {
            showTextDiff()
        }
    }).apply {
        icon = AllIcons.Actions.Diff
        isEnabled = false
    }
    private val hintLabel = JBLabel("Choose a view: overview, before, after, or changed files.")

    private var latestRecord: PsiChangeRecord? = null
    private var selectedView: View = View.OVERVIEW
    private val listener = PsiChangeListener { record -> refresh(record) }

    init {
        viewSelector.addListSelectionListener {
            val selected = viewSelector.selectedValue ?: View.OVERVIEW
            if (selected != selectedView) {
                selectedView = selected
                latestRecord?.let { renderSelectedView(it) }
            }
        }
        border = JBUI.Borders.empty(8)
        add(titleLabel, BorderLayout.NORTH)
        add(buildBody(), BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)

        service.addListener(listener)
        refresh(service.latest())
    }

    override fun dispose() {
        service.removeListener(listener)
    }

    private fun buildBody(): JPanel {
        val selectorPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.emptyTop(8)
            add(viewSelectorLabel)
            add(ScrollPaneFactory.createScrollPane(viewSelector).apply { border = JBUI.Borders.emptyTop(4) })
        }

        val asciiPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.emptyTop(8)
            add(asciiTreeLabel)
            add(ScrollPaneFactory.createScrollPane(asciiTreeArea).apply { border = JBUI.Borders.emptyTop(4) })
            add(JPanel().apply { add(diffButton) })
        }

        return BorderLayoutPanel()
            .addToTop(BorderLayoutPanel().apply {
                addToTop(summaryLabel)
                addToCenter(hintLabel)
                addToBottom(selectorPanel)
            })
            .addToCenter(ScrollPaneFactory.createScrollPane(diagram).apply { border = JBUI.Borders.emptyTop(8) })
            .addToBottom(asciiPanel)
    }

    private fun buildFooter(): JPanel {
        return JPanel().apply {
            isVisible = false
        }
    }

    // removed Swing JTree; using diagram component instead

    private fun refresh(record: PsiChangeRecord?) {
        latestRecord = record
        if (record == null) {
            summaryLabel.text = "No PSI changes captured yet."
            diffButton.isEnabled = false
            diagram.setRoot(buildEmptyNode("No PSI changes yet"))
            asciiTreeArea.text = "No PSI changes yet."
            return
        }

        summaryLabel.text = friendlySummary(record)
        diffButton.isEnabled = record.before != null && record.after != null
        if (viewSelector.selectedValue != selectedView) {
            viewSelector.selectedIndex = View.entries.indexOf(selectedView).coerceAtLeast(0)
        }
        renderSelectedView(record)
    }

    private fun showTextDiff() {
        val record = latestRecord ?: return
        val before = record.before ?: return
        val after = record.after ?: return

        val request = SimpleDiffRequest(
            "PSI Agent: ${record.toolName}",
            DiffContentFactory.getInstance().create(project, before.fullText),
            DiffContentFactory.getInstance().create(project, after.fullText),
            "Before",
            "After"
        )
        DiffManager.getInstance().showDiff(project, request)
    }

    private fun renderSelectedView(record: PsiChangeRecord) {
        val root = when (selectedView) {
            View.OVERVIEW -> buildOverviewTree(record)
            View.BEFORE -> record.before?.let { snapshotToTree(it.root, "Before PSI") } ?: buildEmptyNode("Before snapshot unavailable")
            View.AFTER -> record.after?.let { snapshotToTree(it.root, "After PSI") } ?: buildEmptyNode("After snapshot unavailable")
            View.AFFECTED_FILES -> buildAffectedFilesTree(record)
        }
        diagram.setRoot(root)
        asciiTreeArea.text = renderAsciiTree(root)
    }

    private fun setTreeModel(tree: Tree, root: PsiTreePresentationNode?) {
        val modelRoot = DefaultMutableTreeNode(root ?: PsiTreePresentationNode("No snapshot", PsiNodeChangeStatus.UNCHANGED, ""))
        if (root != null) {
            populateNode(modelRoot, root)
        }
        tree.model = DefaultTreeModel(modelRoot)
        TreeUtil.expandAll(tree)
    }

    private fun populateNode(parent: DefaultMutableTreeNode, node: PsiTreePresentationNode) {
        node.children.forEach { child ->
            val childNode = DefaultMutableTreeNode(child)
            parent.add(childNode)
            populateNode(childNode, child)
        }
    }

    private fun buildOverviewTree(record: PsiChangeRecord): PsiTreePresentationNode {
        val children = mutableListOf<PsiTreePresentationNode>()
        children += node("Status", if (record.success) "Success" else "Failed", record.message, if (record.success) PsiNodeChangeStatus.UNCHANGED else PsiNodeChangeStatus.REMOVED)
        children += node("Tool", record.toolName, "Refactoring tool name", PsiNodeChangeStatus.UNCHANGED)
        children += node("File", record.filePath, "Primary file that changed", PsiNodeChangeStatus.UNCHANGED)
        if (record.affectedFiles.isNotEmpty()) {
            children += PsiTreePresentationNode(
                label = "Affected files (${record.affectedFiles.size})",
                status = PsiNodeChangeStatus.UNCHANGED,
                tooltip = "Files touched by the refactoring",
                children = record.affectedFiles.mapIndexed { index, file ->
                    PsiTreePresentationNode(
                        label = "${index + 1}. $file",
                        status = PsiNodeChangeStatus.UNCHANGED,
                        tooltip = file
                    )
                }
            )
        }

        return PsiTreePresentationNode(
            label = friendlySummary(record),
            status = if (record.success) PsiNodeChangeStatus.UNCHANGED else PsiNodeChangeStatus.REMOVED,
            tooltip = record.message,
            children = children
        )
    }

    private fun buildAffectedFilesTree(record: PsiChangeRecord): PsiTreePresentationNode {
        if (record.affectedFiles.isEmpty()) return buildEmptyNode("No affected files")
        return PsiTreePresentationNode(
            label = "Affected files",
            status = PsiNodeChangeStatus.UNCHANGED,
            tooltip = "Files changed by the latest PSI operation",
            children = record.affectedFiles.mapIndexed { index, file ->
                PsiTreePresentationNode(
                    label = "${index + 1}. $file",
                    status = PsiNodeChangeStatus.UNCHANGED,
                    tooltip = file
                )
            }
        )
    }

    private fun snapshotToTree(snapshot: PsiSnapshotNode, title: String): PsiTreePresentationNode {
        return PsiTreePresentationNode(
            label = title,
            status = PsiNodeChangeStatus.UNCHANGED,
            tooltip = snapshot.text,
            children = listOf(snapshotNodeToTree(snapshot))
        )
    }

    private fun snapshotNodeToTree(node: PsiSnapshotNode): PsiTreePresentationNode {
        return PsiTreePresentationNode(
            label = prettyNodeLabel(node),
            status = if (node.truncated) PsiNodeChangeStatus.CHANGED else PsiNodeChangeStatus.UNCHANGED,
            tooltip = node.text,
            children = node.children.map { snapshotNodeToTree(it) }
        )
    }

    private fun buildEmptyNode(message: String): PsiTreePresentationNode {
        return PsiTreePresentationNode(message, PsiNodeChangeStatus.UNCHANGED, message)
    }

    private fun renderAsciiTree(root: PsiTreePresentationNode?): String {
        if (root == null) return "No PSI changes yet."

        return buildString {
            appendLine(root.label)
            root.children.forEachIndexed { index, child ->
                append(renderAsciiNode(child, prefix = "", isLast = index == root.children.lastIndex))
            }
        }.trimEnd()
    }

    private fun renderAsciiNode(node: PsiTreePresentationNode, prefix: String, isLast: Boolean): String {
        val connector = if (isLast) "└─ " else "├─ "
        val nextPrefix = prefix + if (isLast) "   " else "│  "
        val childText = if (node.children.isEmpty()) "" else "\n" + node.children.mapIndexed { index, child ->
            renderAsciiNode(child, nextPrefix, index == node.children.lastIndex)
        }.joinToString("\n")

        return buildString {
            append(prefix)
            append(connector)
            append(node.label)
            append(childText)
        }
    }

    private fun prettyNodeLabel(node: PsiSnapshotNode): String {
        val text = node.text.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
        return "${node.type}: ${node.name}$text"
    }

    private fun friendlySummary(record: PsiChangeRecord): String {
        val status = if (record.success) "success" else "failed"
        return "${record.toolName.replace('_', ' ')} — $status"
    }

    private fun node(title: String, value: String, tooltip: String, status: PsiNodeChangeStatus): PsiTreePresentationNode {
        return PsiTreePresentationNode(
            label = "$title: $value",
            status = status,
            tooltip = tooltip
        )
    }

    private class SnapshotRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = (value as? DefaultMutableTreeNode)?.userObject as? PsiTreePresentationNode
                ?: run {
                    append(value.toString())
                    return
                }

            val attributes = when (node.status) {
                PsiNodeChangeStatus.UNCHANGED -> SimpleTextAttributes.REGULAR_ATTRIBUTES
                PsiNodeChangeStatus.CHANGED -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                PsiNodeChangeStatus.ADDED -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                PsiNodeChangeStatus.REMOVED -> SimpleTextAttributes.ERROR_ATTRIBUTES
            }

            append(node.label, attributes)
            if (node.tooltip.isNotBlank()) {
                toolTipText = node.tooltip
            }
        }
    }

    private enum class View(val label: String) {
        OVERVIEW("Overview"),
        BEFORE("Before PSI"),
        AFTER("After PSI"),
        AFFECTED_FILES("Affected files");

        override fun toString(): String = label
    }
}





