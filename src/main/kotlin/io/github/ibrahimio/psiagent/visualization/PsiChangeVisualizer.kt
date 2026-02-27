package io.github.ibrahimio.psiagent.visualization

import io.github.ibrahimio.psiagent.refactoring.PsiNodeSnapshot
import io.github.ibrahimio.psiagent.refactoring.RenameResult

data class PsiDiffNode(
    val type: String,
    val name: String,
    val before: String?,
    val after: String?,
    val changed: Boolean,
    val filePath: String,
    val lineNumber: Int
)

data class VisualizationReport(
    val operation: String,
    val summary: String,
    val affectedFiles: List<String>,
    val diffs: List<PsiDiffNode>,
    val jsonTree: String
)

class PsiChangeVisualizer {

    fun visualize(result: RenameResult): VisualizationReport {
        val diffs = buildDiffs(result.psiNodesBefore, result.psiNodesAfter)
        val jsonTree = buildJsonTree(result)
        val summary = buildSummary(result, diffs)

        return VisualizationReport(
            operation = "RENAME_METHOD",
            summary = summary,
            affectedFiles = result.affectedFiles,
            diffs = diffs,
            jsonTree = jsonTree
        )
    }

    fun printReport(report: VisualizationReport): String {
        val sb = StringBuilder()
        sb.appendLine("=".repeat(60))
        sb.appendLine("PSI Change Visualization Report")
        sb.appendLine("=".repeat(60))
        sb.appendLine("Operation : ${report.operation}")
        sb.appendLine("Summary   : ${report.summary}")
        sb.appendLine()

        if (report.affectedFiles.isNotEmpty()) {
            sb.appendLine("Affected Files:")
            report.affectedFiles.forEach { sb.appendLine("  - $it") }
            sb.appendLine()
        }

        if (report.diffs.isNotEmpty()) {
            sb.appendLine("PSI Node Changes:")
            sb.appendLine("-".repeat(60))
            report.diffs.forEach { diff ->
                sb.appendLine("  [${diff.type}] ${diff.filePath}:${diff.lineNumber}")
                if (diff.changed) {
                    sb.appendLine("    BEFORE: ${diff.before?.take(120)}")
                    sb.appendLine("    AFTER : ${diff.after?.take(120)}")
                } else {
                    sb.appendLine("    (unchanged) ${diff.name}")
                }
            }
            sb.appendLine()
        }

        sb.appendLine("PSI Tree (JSON):")
        sb.appendLine("-".repeat(60))
        sb.appendLine(report.jsonTree)
        sb.appendLine("=".repeat(60))
        return sb.toString()
    }

    private fun buildDiffs(before: List<PsiNodeSnapshot>, after: List<PsiNodeSnapshot>): List<PsiDiffNode> {
        val diffs = mutableListOf<PsiDiffNode>()

        // Pair up nodes by position (filePath + lineNumber)
        val beforeMap = before.associateBy { "${it.filePath}:${it.lineNumber}" }
        val afterMap = after.associateBy { "${it.filePath}:${it.lineNumber}" }

        val allKeys = (beforeMap.keys + afterMap.keys).distinct()
        for (key in allKeys) {
            val b = beforeMap[key]
            val a = afterMap[key]
            val changed = b?.text != a?.text || b?.name != a?.name
            diffs.add(
                PsiDiffNode(
                    type = b?.type ?: a?.type ?: "UNKNOWN",
                    name = b?.name ?: a?.name ?: "",
                    before = b?.text,
                    after = a?.text,
                    changed = changed,
                    filePath = b?.filePath ?: a?.filePath ?: "",
                    lineNumber = b?.lineNumber ?: a?.lineNumber ?: -1
                )
            )
        }
        return diffs
    }

    private fun buildSummary(result: RenameResult, diffs: List<PsiDiffNode>): String {
        val changedCount = diffs.count { it.changed }
        return if (result.success) {
            "Renamed '${result.oldName}' → '${result.newName}' " +
                "($changedCount node(s) changed across ${result.affectedFiles.size} file(s))"
        } else {
            "Rename failed: ${result.message}"
        }
    }

    private fun buildJsonTree(result: RenameResult): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"operation\": \"rename_method\",")
        sb.appendLine("  \"success\": ${result.success},")
        sb.appendLine("  \"oldName\": \"${result.oldName}\",")
        sb.appendLine("  \"newName\": \"${result.newName}\",")
        sb.appendLine("  \"affectedFiles\": ${jsonArray(result.affectedFiles)},")
        sb.appendLine("  \"before\": ${jsonSnapshots(result.psiNodesBefore)},")
        sb.appendLine("  \"after\": ${jsonSnapshots(result.psiNodesAfter)}")
        sb.append("}")
        return sb.toString()
    }

    private fun jsonArray(items: List<String>): String =
        items.joinToString(", ", "[", "]") { "\"${jsonEscape(it)}\"" }

    private fun jsonSnapshots(snapshots: List<PsiNodeSnapshot>): String {
        if (snapshots.isEmpty()) return "[]"
        val entries = snapshots.joinToString(",\n    ") { s ->
            """{"type": "${jsonEscape(s.type)}", "name": "${jsonEscape(s.name)}", "file": "${jsonEscape(s.filePath)}", "line": ${s.lineNumber}, "text": "${jsonEscape(s.text.take(200))}"}"""
        }
        return "[\n    $entries\n  ]"
    }

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
