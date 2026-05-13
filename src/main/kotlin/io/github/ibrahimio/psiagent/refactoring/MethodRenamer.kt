package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtNamedFunction

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
    ): RenameResult = renameSymbol(filePath, oldMethodName, newMethodName)

    fun renameSymbol(
        filePath: String,
        oldSymbolName: String,
        newSymbolName: String
    ): RenameResult {
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
            ?: VirtualFileManager.getInstance().findFileByUrl(filePath)
            ?: LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return RenameResult(
                success = false,
                message = "File not found: $filePath",
                oldName = oldSymbolName,
                newName = newSymbolName,
                affectedFiles = emptyList(),
                psiNodesBefore = emptyList(),
                psiNodesAfter = emptyList()
            )

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            ?: return RenameResult(
                success = false,
                message = "Cannot parse file: $filePath",
                oldName = oldSymbolName,
                newName = newSymbolName,
                affectedFiles = emptyList(),
                psiNodesBefore = emptyList(),
                psiNodesAfter = emptyList()
            )

        val symbol = findSymbolInFile(psiFile, oldSymbolName)
            ?: return RenameResult(
                success = false,
                message = "Symbol '$oldSymbolName' not found in $filePath",
                oldName = oldSymbolName,
                newName = newSymbolName,
                affectedFiles = emptyList(),
                psiNodesBefore = emptyList(),
                psiNodesAfter = emptyList()
            )

        val snapshotBefore = listOf(snapshotPsiNode(symbol, filePath))

        val affectedFiles = mutableListOf<String>()
        try {
            WriteCommandAction.runWriteCommandAction(project, "Rename Symbol '$oldSymbolName' to '$newSymbolName'", null, {
                val processor = RenameProcessor(project, symbol, newSymbolName, false, false)
                val usages = processor.findUsages()
                usages.mapTo(affectedFiles) { it.element?.containingFile?.virtualFile?.path ?: filePath }
                processor.run()
            })
        } catch (e: Exception) {
            return RenameResult(
                success = false,
                message = "Rename failed: ${e.message}",
                oldName = oldSymbolName,
                newName = newSymbolName,
                affectedFiles = emptyList(),
                psiNodesBefore = snapshotBefore,
                psiNodesAfter = emptyList()
            )
        }

        val renamedSymbol = findSymbolInFile(psiFile, newSymbolName)
        val snapshotAfter = if (renamedSymbol != null) {
            listOf(snapshotPsiNode(renamedSymbol, filePath))
        } else {
            emptyList()
        }

        if (affectedFiles.isEmpty()) {
            affectedFiles.add(filePath)
        }

        return RenameResult(
            success = true,
            message = "Successfully renamed '$oldSymbolName' to '$newSymbolName'",
            oldName = oldSymbolName,
            newName = newSymbolName,
            affectedFiles = affectedFiles.distinct(),
            psiNodesBefore = snapshotBefore,
            psiNodesAfter = snapshotAfter
        )
    }

    fun findMethodInFile(psiFile: PsiFile, methodName: String): PsiNamedElement? {
        return findSymbolInFile(psiFile, methodName)
    }

    fun findSymbolInFile(psiFile: PsiFile, symbolName: String): PsiNamedElement? {
        PsiTreeUtil.collectElements(psiFile) { it is PsiMethod }
            .filterIsInstance<PsiMethod>()
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        PsiTreeUtil.collectElements(psiFile) { it is PsiClass }
            .filterIsInstance<PsiClass>()
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        PsiTreeUtil.collectElements(psiFile) { it is PsiField }
            .filterIsInstance<PsiField>()
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        PsiTreeUtil.collectElements(psiFile) { it is KtNamedFunction }
            .filterIsInstance<KtNamedFunction>()
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        PsiTreeUtil.collectElements(psiFile) { it is KtClassOrObject }
            .filterIsInstance<KtClassOrObject>()
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        PsiTreeUtil.collectElements(psiFile) { it is KtProperty }
            .filterIsInstance<KtProperty>()
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        return PsiTreeUtil.collectElements(psiFile) { it is PsiNamedElement }
            .filterIsInstance<PsiNamedElement>()
            .firstOrNull { it.name == symbolName }
    }

    fun snapshotPsiNode(element: PsiElement, filePath: String): PsiNodeSnapshot {
        val file = element.containingFile
        val document = com.intellij.psi.PsiDocumentManager.getInstance(element.project)
            .getDocument(file)
        val lineNumber = document?.getLineNumber(element.textOffset)?.plus(1) ?: -1

        val name = when (element) {
            is PsiMethod -> element.name
            is KtNamedFunction -> element.name ?: "<unnamed>"
            is PsiClass -> element.name ?: "<anonymous>"
            is PsiNamedElement -> element.name ?: "<unnamed>"
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
