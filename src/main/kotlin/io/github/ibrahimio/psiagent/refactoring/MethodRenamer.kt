package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiField
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
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
    ): RenameResult {
        return renameSymbol(filePath, oldMethodName, newMethodName)
    }

    fun renameSymbol(
        filePath: String,
        oldName: String,
        newName: String
    ): RenameResult {
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
            ?: VirtualFileManager.getInstance().findFileByUrl(filePath)
            ?: LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return RenameResult(
                success = false,
                message = "File not found: $filePath",
                oldName = oldName,
                newName = newName,
                affectedFiles = emptyList(),
                psiNodesBefore = emptyList(),
                psiNodesAfter = emptyList()
            )

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            ?: return RenameResult(
                success = false,
                message = "Cannot parse file: $filePath",
                oldName = oldName,
                newName = newName,
                affectedFiles = emptyList(),
                psiNodesBefore = emptyList(),
                psiNodesAfter = emptyList()
            )

        val method = findSymbolInFile(psiFile, oldName)
            ?: return RenameResult(
                success = false,
                message = "Symbol '$oldName' not found in $filePath",
                oldName = oldName,
                newName = newName,
                affectedFiles = emptyList(),
                psiNodesBefore = emptyList(),
                psiNodesAfter = emptyList()
            )

        val snapshotBefore = listOf(snapshotPsiNode(method, filePath))

        val affectedFiles = mutableListOf<String>()
        try {
            WriteCommandAction.runWriteCommandAction(project, "Rename Symbol '$oldName' to '$newName'", null, {
                val processor = RenameProcessor(project, method, newName, false, false)
                val usages = processor.findUsages()
                usages.mapTo(affectedFiles) { it.element?.containingFile?.virtualFile?.path ?: filePath }
                processor.run()
            })
        } catch (e: Exception) {
            return RenameResult(
                success = false,
                message = "Rename failed: ${e.message}",
                oldName = oldName,
                newName = newName,
                affectedFiles = emptyList(),
                psiNodesBefore = snapshotBefore,
                psiNodesAfter = emptyList()
            )
        }

        val renamedMethod = findSymbolInFile(psiFile, newName)
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
            message = "Successfully renamed '$oldName' to '$newName'",
            oldName = oldName,
            newName = newName,
            affectedFiles = affectedFiles.distinct(),
            psiNodesBefore = snapshotBefore,
            psiNodesAfter = snapshotAfter
        )
    }

    fun findMethodInFile(psiFile: PsiFile, methodName: String): PsiNamedElement? {
        return findSymbolInFile(psiFile, methodName)
    }

    fun findSymbolInFile(psiFile: PsiFile, symbolName: String): PsiNamedElement? {
        // Search Java/Kotlin methods and functions
        PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        // Search Java/Kotlin classes and interfaces
        PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        // Search Kotlin functions
        PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java)
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        // Search Java fields
        PsiTreeUtil.findChildrenOfType(psiFile, PsiField::class.java)
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        // Search all other named elements (variables, parameters, properties, aliases, etc.)
        // This is a fallback to catch any other renamable PSI node types
        PsiTreeUtil.findChildrenOfType(psiFile, PsiNamedElement::class.java)
            .filter { it !is PsiMethod && it !is PsiClass && it !is KtNamedFunction && it !is PsiField }
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        return null
    }

    fun snapshotPsiNode(element: PsiElement, filePath: String): PsiNodeSnapshot {
        val file = element.containingFile
        val document = com.intellij.psi.PsiDocumentManager.getInstance(element.project)
            .getDocument(file)
        val lineNumber = document?.getLineNumber(element.textOffset)?.plus(1) ?: -1

        val name = when (element) {
            is PsiMethod -> element.name
            is PsiClass -> element.name ?: "<anonymous>"
            is PsiField -> element.name
            is KtNamedFunction -> element.name ?: "<unnamed>"
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
