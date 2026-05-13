package io.github.ibrahimio.psiagent.visualization

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class PsiSnapshotSerializer(private val project: Project) {

    fun snapshot(filePath: String): PsiFileSnapshot? {
        return ReadAction.compute<PsiFileSnapshot?, RuntimeException> {
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
                ?: com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(filePath)
                ?: com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
                ?: return@compute null

            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                ?: return@compute null

            snapshot(psiFile)
        }
    }

    fun snapshot(psiFile: PsiFile): PsiFileSnapshot {
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
        val fullText = document?.text ?: psiFile.text
        val filePath = psiFile.virtualFile?.path ?: psiFile.name
        val root = serializeElement(psiFile)
        return PsiFileSnapshot(
            filePath = filePath,
            fileName = psiFile.name,
            fullText = fullText,
            root = root
        )
    }

    private fun serializeElement(element: PsiElement, depth: Int = 0): PsiSnapshotNode {
        val text = element.text
        val children = if (depth >= MAX_DEPTH) {
            emptyList()
        } else {
            element.children
                .asSequence()
                .filterNot { it is PsiWhiteSpace || it is PsiComment }
                .take(MAX_CHILDREN)
                .map { serializeElement(it, depth + 1) }
                .toList()
        }

        val truncated = depth < MAX_DEPTH && element.children.count { it !is PsiWhiteSpace && it !is PsiComment } > MAX_CHILDREN

        return PsiSnapshotNode(
            type = nodeType(element),
            name = nodeName(element),
            text = snippet(text),
            children = children,
            truncated = truncated
        )
    }

    private fun nodeType(element: PsiElement): String = when (element) {
        is PsiFile -> element.javaClass.simpleName
        is PsiClass -> "Class"
        is PsiMethod -> "Method"
        is PsiField -> "Field"
        is KtNamedDeclaration -> element.javaClass.simpleName.removePrefix("Kt")
        is PsiNamedElement -> element.javaClass.simpleName
        else -> element.javaClass.simpleName
    }

    private fun nodeName(element: PsiElement): String = when (element) {
        is PsiMethod -> element.name
        is PsiClass -> element.name ?: "<anonymous>"
        is PsiField -> element.name
        is KtNamedDeclaration -> element.name ?: "<unnamed>"
        is PsiNamedElement -> element.name ?: "<unnamed>"
        else -> element.textRange?.let { rangeLabel(it) } ?: "<anonymous>"
    }

    private fun rangeLabel(range: TextRange): String = "${range.startOffset + 1}-${range.endOffset}"

    private fun snippet(text: String): String {
        val normalized = text
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\t", " ")
            .trim()
        return if (normalized.length <= TEXT_LIMIT) normalized else normalized.take(TEXT_LIMIT) + "…"
    }

    companion object {
        private const val MAX_DEPTH = 5
        private const val MAX_CHILDREN = 40
        private const val TEXT_LIMIT = 160
    }
}



