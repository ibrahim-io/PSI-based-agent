package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiCodeBlock
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile


data class IntroduceVariableResult(
    val success: Boolean,
    val message: String,
    val file: String,
    val variableName: String,
    val affectedFiles: List<String> = emptyList()
)

class IntroduceVariableProcessor(private val project: Project) {

    fun introduceVariable(
        filePath: String,
        variableName: String,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int
    ): IntroduceVariableResult {
        if (variableName.isBlank()) {
            return IntroduceVariableResult(
                success = false,
                message = "Variable name cannot be blank",
                file = filePath,
                variableName = variableName
            )
        }

        if (startLine < 1 || endLine < startLine || startColumn < 1 || endColumn < startColumn) {
            return IntroduceVariableResult(
                success = false,
                message = "Invalid range: start=$startLine:$startColumn end=$endLine:$endColumn",
                file = filePath,
                variableName = variableName
            )
        }

        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
            ?: VirtualFileManager.getInstance().findFileByUrl(filePath)
            ?: LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return IntroduceVariableResult(
                success = false,
                message = "File not found: $filePath",
                file = filePath,
                variableName = variableName
            )

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            ?: return IntroduceVariableResult(
                success = false,
                message = "Cannot parse file: $filePath",
                file = filePath,
                variableName = variableName
            )

        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: return IntroduceVariableResult(
                success = false,
                message = "Cannot get document for file: $filePath",
                file = filePath,
                variableName = variableName
            )

        PsiDocumentManager.getInstance(project).commitDocument(document)

        val startOffset = lineColumnToOffset(document, startLine, startColumn)
        val endOffsetExclusive = lineColumnToOffset(document, endLine, endColumn) + 1

        if (startOffset < 0 || endOffsetExclusive <= startOffset || endOffsetExclusive > document.textLength) {
            return IntroduceVariableResult(
                success = false,
                message = "Invalid selection range for $startLine:$startColumn..$endLine:$endColumn",
                file = filePath,
                variableName = variableName
            )
        }

        val selectedText = document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffsetExclusive)).trim()
            .removeSuffix(";")
            .trim()

        if (selectedText.isBlank()) {
            return IntroduceVariableResult(
                success = false,
                message = "Selection is empty",
                file = filePath,
                variableName = variableName
            )
        }

        val startElement = psiFile.findElementAt(startOffset)
            ?: return IntroduceVariableResult(
                success = false,
                message = "Unable to resolve PSI element at selection start",
                file = filePath,
                variableName = variableName
            )

        val endElement = psiFile.findElementAt((endOffsetExclusive - 1).coerceAtLeast(startOffset)) ?: startElement
        val anchor = findAnchor(startElement, endElement, psiFile)
            ?: return IntroduceVariableResult(
                success = false,
                message = "Unable to find a statement body for introduce-variable",
                file = filePath,
                variableName = variableName
            )

        val affectedFiles = linkedSetOf(filePath)
        val isKotlin = psiFile is KtFile
        val declarationKeyword = if (isKotlin) "val" else "var"
        val declarationText = buildDeclarationText(
            declarationKeyword = declarationKeyword,
            variableName = variableName,
            expressionText = selectedText,
            isKotlin = isKotlin,
            indentation = lineIndent(document, anchor.textRange.startOffset)
        )

        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                document.replaceString(startOffset, endOffsetExclusive, variableName)
                document.insertString(anchor.textRange.startOffset, declarationText)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            val declaredFile = psiFile.virtualFile?.path ?: filePath
            affectedFiles.add(declaredFile)

            IntroduceVariableResult(
                success = true,
                message = "Successfully introduced variable '$variableName'",
                file = filePath,
                variableName = variableName,
                affectedFiles = affectedFiles.toList()
            )
        } catch (e: Exception) {
            IntroduceVariableResult(
                success = false,
                message = "Introduce variable failed: ${e.message}",
                file = filePath,
                variableName = variableName,
                affectedFiles = affectedFiles.toList()
            )
        }
    }

    private fun findAnchor(startElement: PsiElement, endElement: PsiElement, psiFile: PsiFile): PsiElement? {
        if (psiFile is KtFile) {
            var current: PsiElement? = startElement
            while (current != null && current !is KtBlockExpression) {
                if (current is KtExpression && current.parent is KtBlockExpression) {
                    return current
                }
                current = current.parent
            }
            return PsiTreeUtil.getParentOfType(startElement, KtExpression::class.java, false)
                ?.takeIf { it.parent is KtBlockExpression }
                ?: PsiTreeUtil.getParentOfType(endElement, KtExpression::class.java, false)
                    ?.takeIf { it.parent is KtBlockExpression }
        }

        var current: PsiElement? = startElement
        while (current != null && current !is PsiCodeBlock) {
            if (current is PsiStatement && current.parent is PsiCodeBlock) {
                return current
            }
            current = current.parent
        }
        return PsiTreeUtil.getParentOfType(startElement, PsiStatement::class.java, false)
            ?.takeIf { it.parent is PsiCodeBlock }
            ?: PsiTreeUtil.getParentOfType(endElement, PsiStatement::class.java, false)
                ?.takeIf { it.parent is PsiCodeBlock }
    }

    private fun buildDeclarationText(
        declarationKeyword: String,
        variableName: String,
        expressionText: String,
        isKotlin: Boolean,
        indentation: String
    ): String {
        val normalizedExpression = expressionText.trim().removeSuffix(";").trim()
        val suffix = if (isKotlin) "" else ";"
        val lineSeparator = "\n"
        return "$indentation$declarationKeyword $variableName = $normalizedExpression$suffix$lineSeparator"
    }

    private fun lineColumnToOffset(document: com.intellij.openapi.editor.Document, line: Int, column: Int): Int {
        val lineIndex = (line - 1).coerceIn(0, document.lineCount - 1)
        val lineStart = document.getLineStartOffset(lineIndex)
        val lineEnd = document.getLineEndOffset(lineIndex)
        return (lineStart + column - 1).coerceIn(lineStart, lineEnd)
    }

    private fun lineIndent(document: com.intellij.openapi.editor.Document, offset: Int): String {
        val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
        val lineEnd = document.getLineEndOffset(document.getLineNumber(offset))
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
        return lineText.takeWhile { it.isWhitespace() }
    }
}



