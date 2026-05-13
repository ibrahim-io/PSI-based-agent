package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiMethodCallExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression


data class InlineMethodResult(
    val success: Boolean,
    val message: String,
    val file: String,
    val methodName: String,
    val affectedFiles: List<String> = emptyList()
)

class InlineMethodProcessor(private val project: Project) {

    fun inlineMethod(filePath: String, methodName: String): InlineMethodResult {
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
            ?: VirtualFileManager.getInstance().findFileByUrl(filePath)
            ?: LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return InlineMethodResult(
                success = false,
                message = "File not found: $filePath",
                file = filePath,
                methodName = methodName
            )

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            ?: return InlineMethodResult(
                success = false,
                message = "Cannot parse file: $filePath",
                file = filePath,
                methodName = methodName
            )

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val target = findInlineTarget(psiFile, methodName)
            ?: return InlineMethodResult(
                success = false,
                message = "Inline target '$methodName' not found in $filePath",
                file = filePath,
                methodName = methodName
            )

        val replacementText = extractReplacementText(target)
            ?: return InlineMethodResult(
                success = false,
                message = "Inline target '$methodName' is not a supported simple method/function body",
                file = filePath,
                methodName = methodName
            )

        val references = ReferencesSearch.search(target, GlobalSearchScope.projectScope(project), true)
            .findAll()
            .toList()

        if (references.isEmpty()) {
            return InlineMethodResult(
                success = false,
                message = "No usages found for '$methodName'",
                file = filePath,
                methodName = methodName
            )
        }

        val affectedFiles = linkedSetOf(filePath)

        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                references.forEach { reference ->
                    val usageElement = reference.element
                    val callExpression = PsiTreeUtil.getParentOfType(usageElement, PsiMethodCallExpression::class.java, false)
                        ?: PsiTreeUtil.getParentOfType(usageElement, KtCallExpression::class.java, false)
                        ?: return@forEach

                    val file = callExpression.containingFile
                    file.virtualFile?.path?.let { affectedFiles.add(it) }

                    when (callExpression) {
                        is PsiMethodCallExpression -> {
                            val expr = JavaPsiFacade.getElementFactory(project)
                                .createExpressionFromText(replacementText, callExpression)
                            callExpression.replace(expr)
                        }
                        is KtCallExpression -> {
                            val expr = KtPsiFactory(project).createExpression(replacementText)
                            callExpression.replace(expr)
                        }
                    }
                }

                target.delete()
            }

            InlineMethodResult(
                success = true,
                message = "Successfully inlined '$methodName'",
                file = filePath,
                methodName = methodName,
                affectedFiles = affectedFiles.toList()
            )
        } catch (e: Exception) {
            InlineMethodResult(
                success = false,
                message = "Inline failed: ${e.message}",
                file = filePath,
                methodName = methodName,
                affectedFiles = affectedFiles.toList()
            )
        }
    }

    private fun findInlineTarget(psiFile: PsiFile, methodName: String): PsiElement? {
        PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            .firstOrNull { it.name == methodName && it.parameterList.parametersCount == 0 }
            ?.let { return it }

        if (psiFile is KtFile) {
            PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java)
                .firstOrNull { it.name == methodName && it.valueParameters.isEmpty() }
                ?.let { return it }
        }

        return null
    }

    private fun extractReplacementText(target: PsiElement): String? {
        return when (target) {
            is PsiMethod -> extractJavaReplacement(target)
            is KtNamedFunction -> extractKotlinReplacement(target)
            else -> null
        }
    }

    private fun extractJavaReplacement(method: PsiMethod): String? {
        val body = method.body ?: return null
        val statements = body.statements
        if (statements.size != 1) return null
        val statement = statements.single()
        val returnStatement = statement as? PsiReturnStatement ?: return null
        return returnStatement.returnValue?.text
    }

    private fun extractKotlinReplacement(function: KtNamedFunction): String? {
        val bodyExpression = function.bodyExpression ?: return null
        if (bodyExpression is KtBlockExpression) {
            val statements = bodyExpression.statements
            if (statements.size != 1) return null
            val returnExpression = statements.single() as? KtReturnExpression ?: return null
            return returnExpression.returnedExpression?.text
        }
        return bodyExpression.text
    }
}


