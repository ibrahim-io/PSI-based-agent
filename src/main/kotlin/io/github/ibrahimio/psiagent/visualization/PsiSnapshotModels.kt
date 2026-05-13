package io.github.ibrahimio.psiagent.visualization

data class PsiSnapshotNode(
    val type: String,
    val name: String,
    val text: String,
    val children: List<PsiSnapshotNode> = emptyList(),
    val truncated: Boolean = false
)

data class PsiFileSnapshot(
    val filePath: String,
    val fileName: String,
    val fullText: String,
    val root: PsiSnapshotNode
)

data class PsiChangeRecord(
    val toolName: String,
    val filePath: String,
    val success: Boolean,
    val message: String,
    val affectedFiles: List<String>,
    val before: PsiFileSnapshot?,
    val after: PsiFileSnapshot?,
    val timestampMillis: Long = System.currentTimeMillis()
)

enum class PsiNodeChangeStatus {
    UNCHANGED,
    CHANGED,
    ADDED,
    REMOVED
}

data class PsiTreePresentationNode(
    val label: String,
    val status: PsiNodeChangeStatus,
    val tooltip: String,
    val children: List<PsiTreePresentationNode> = emptyList()
)

