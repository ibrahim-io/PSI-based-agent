package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor

data class PsiNodeSnapshot(
    val type: String,
    val name: String,
    val text: String,
    val filePath: String,
    val lineNumber: Int
)

data class RenameResult(
    val success: Boolean,
    val message: String,
    val oldName: String,
    val newName: String,
    val affectedFiles: List<String>,
    val psiNodesBefore: List<PsiNodeSnapshot>,
    val psiNodesAfter: List<PsiNodeSnapshot>
)

class MethodRenamer(private val project: Project) {

    fun renameMethod(
        filePath: String,
        oldMethodName: String,
        newMethodName: String
    ): RenameResult {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return RenameResult(
                success = false,
                message = "File not found: $filePath",
                oldName = oldMethodName,
                newName = newMethodName,
                affectedFiles = emptyList(),
                psiNodesBefore = emptyList(),
                psiNodesAfter = emptyList()
            )

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            ?: return RenameResult(
                success = false,
                message = "Cannot parse file: $filePath",
                oldName = oldMethodName,
                newName = newMethodName,
                affectedFiles = emptyList(),
                psiNodesBefore = emptyList(),
                psiNodesAfter = emptyList()
            )

        val method = findMethodInFile(psiFile, oldMethodName)
            ?: return RenameResult(
                success = false,
                message = "Method '$oldMethodName' not found in $filePath",
                oldName = oldMethodName,
                newName = newMethodName,
                affectedFiles = emptyList(),
                psiNodesBefore = emptyList(),
                psiNodesAfter = emptyList()
            )

        val snapshotBefore = listOf(snapshotPsiNode(method, filePath))

        val affectedFiles = mutableListOf<String>()
        WriteCommandAction.runWriteCommandAction(project, "Rename Method '$oldMethodName' to '$newMethodName'", null, {
            val processor = RenameProcessor(project, method, newMethodName, false, false)
            processor.run()
            processor.usages.mapTo(affectedFiles) { it.file?.virtualFile?.path ?: filePath }
        })

        val renamedMethod = findMethodInFile(psiFile, newMethodName)
        val snapshotAfter = if (renamedMethod != null) {
            listOf(snapshotPsiNode(renamedMethod, filePath))
        } else {
            emptyList()
        }

        if (affectedFiles.isEmpty()) {
            affectedFiles.add(filePath)
        }

        return RenameResult(
            success = true,
            message = "Successfully renamed '$oldMethodName' to '$newMethodName'",
            oldName = oldMethodName,
            newName = newMethodName,
            affectedFiles = affectedFiles.distinct(),
            psiNodesBefore = snapshotBefore,
            psiNodesAfter = snapshotAfter
        )
    }

    fun findMethodInFile(psiFile: PsiFile, methodName: String): PsiMethod? {
        return PsiTreeUtil.collectElements(psiFile) { it is PsiMethod }
            .filterIsInstance<PsiMethod>()
            .firstOrNull { it.name == methodName }
    }

    fun snapshotPsiNode(element: PsiElement, filePath: String): PsiNodeSnapshot {
        val file = element.containingFile
        val document = com.intellij.psi.PsiDocumentManager.getInstance(element.project)
            .getDocument(file)
        val lineNumber = document?.getLineNumber(element.textOffset)?.plus(1) ?: -1

        val name = when (element) {
            is PsiMethod -> element.name
            is com.intellij.psi.PsiClass -> element.name ?: "<anonymous>"
            is com.intellij.psi.PsiNamedElement -> element.name ?: "<unnamed>"
            else -> "<unknown>"
        }

        return PsiNodeSnapshot(
            type = element.javaClass.simpleName,
            name = name,
            text = element.text.take(500),
            filePath = filePath,
            lineNumber = lineNumber
        )
    }
}
