package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

data class DeleteSymbolResult(
    val success: Boolean,
    val message: String,
    val file: String,
    val symbolName: String,
    val symbolType: String,
    val affectedFiles: List<String> = emptyList()
)

class DeleteSymbolProcessor(private val project: Project) {

    fun deleteSymbol(
        filePath: String,
        symbolName: String,
        symbolType: String = "all"
    ): DeleteSymbolResult {
        if (symbolName.isBlank()) {
            return DeleteSymbolResult(
                success = false,
                message = "Symbol name cannot be blank",
                file = filePath,
                symbolName = symbolName,
                symbolType = symbolType
            )
        }

        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
            ?: VirtualFileManager.getInstance().findFileByUrl(filePath)
            ?: LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return DeleteSymbolResult(
                success = false,
                message = "File not found: $filePath",
                file = filePath,
                symbolName = symbolName,
                symbolType = symbolType
            )

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            ?: return DeleteSymbolResult(
                success = false,
                message = "Cannot parse file: $filePath",
                file = filePath,
                symbolName = symbolName,
                symbolType = symbolType
            )

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val target = findTarget(psiFile, symbolName, symbolType)
            ?: return DeleteSymbolResult(
                success = false,
                message = "Symbol '$symbolName' not found in $filePath",
                file = filePath,
                symbolName = symbolName,
                symbolType = symbolType
            )

        val affectedFiles = linkedSetOf(filePath)
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                target.containingFile?.virtualFile?.path?.let { affectedFiles.add(it) }
                target.delete()
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }

            DeleteSymbolResult(
                success = true,
                message = "Successfully deleted '$symbolName'",
                file = filePath,
                symbolName = symbolName,
                symbolType = symbolType,
                affectedFiles = affectedFiles.toList()
            )
        } catch (e: Exception) {
            DeleteSymbolResult(
                success = false,
                message = "Delete failed: ${e.message}",
                file = filePath,
                symbolName = symbolName,
                symbolType = symbolType,
                affectedFiles = affectedFiles.toList()
            )
        }
    }

    private fun findTarget(psiFile: PsiFile, symbolName: String, symbolType: String): com.intellij.psi.PsiElement? {
        val normalized = symbolType.lowercase()
        return when (normalized) {
            "method" -> findMethod(psiFile, symbolName)
            "class" -> findClass(psiFile, symbolName)
            else -> findMethod(psiFile, symbolName) ?: findClass(psiFile, symbolName)
        }
    }

    private fun findMethod(psiFile: PsiFile, symbolName: String): com.intellij.psi.PsiElement? {
        PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        if (psiFile is KtFile) {
            PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java)
                .firstOrNull { it.name == symbolName }
                ?.let { return it }
        }

        return null
    }

    private fun findClass(psiFile: PsiFile, symbolName: String): com.intellij.psi.PsiElement? {
        PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
            .firstOrNull { it.name == symbolName }
            ?.let { return it }

        if (psiFile is KtFile) {
            PsiTreeUtil.findChildrenOfType(psiFile, KtClassOrObject::class.java)
                .firstOrNull { it.name == symbolName }
                ?.toLightClass()
                ?.let { return it }
        }

        return null
    }
}

