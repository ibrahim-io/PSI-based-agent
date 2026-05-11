package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler

data class ExtractMethodResult(
    val success: Boolean,
    val message: String,
    val file: String,
    val newMethodName: String,
    val startLine: Int,
    val endLine: Int,
    val affectedFiles: List<String> = emptyList()
)

class MethodExtractor(private val project: Project) {

    fun extractMethod(
        filePath: String,
        newMethodName: String,
        startLine: Int,
        endLine: Int
    ): ExtractMethodResult {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return ExtractMethodResult(
                success = false,
                message = "Extract method is not supported in unit test mode. Run this in a real IDE session.",
                file = filePath,
                newMethodName = newMethodName,
                startLine = startLine,
                endLine = endLine
            )
        }

        val virtualFile = com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
            ?: com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(filePath)
            ?: LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return ExtractMethodResult(
                success = false,
                message = "File not found: $filePath",
                file = filePath,
                newMethodName = newMethodName,
                startLine = startLine,
                endLine = endLine
            )

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            ?: return ExtractMethodResult(
                success = false,
                message = "Cannot parse file: $filePath",
                file = filePath,
                newMethodName = newMethodName,
                startLine = startLine,
                endLine = endLine
            )

        if (psiFile is KtFile) {
            return extractKotlinFunction(psiFile, virtualFile, newMethodName, startLine, endLine)
        }

        if (startLine < 1 || endLine < startLine) {
            return ExtractMethodResult(
                success = false,
                message = "Invalid line range: startLine=$startLine, endLine=$endLine",
                file = filePath,
                newMethodName = newMethodName,
                startLine = startLine,
                endLine = endLine
            )
        }

        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: return ExtractMethodResult(
                success = false,
                message = "Cannot get document for file: $filePath",
                file = filePath,
                newMethodName = newMethodName,
                startLine = startLine,
                endLine = endLine
            )

        try {
            PsiDocumentManager.getInstance(project).commitDocument(document)
            val startOffset = document.getLineStartOffset(startLine - 1)
            val endOffset = document.getLineEndOffset(endLine - 1)

            if (startOffset >= endOffset) {
                return ExtractMethodResult(
                    success = false,
                    message = "Invalid selection range for lines $startLine..$endLine",
                    file = filePath,
                    newMethodName = newMethodName,
                    startLine = startLine,
                    endLine = endLine
                )
            }

            val editorHandle = openEditor(virtualFile, document)
            val editor = editorHandle.editor
            try {
                editor.selectionModel.setSelection(startOffset, endOffset)

                val elements = ExtractMethodHandler.getElements(project, editor, psiFile)
                    ?.filterNotNull()
                    ?.filterNot { it is PsiWhiteSpace }
                    ?.toTypedArray()
                    ?: emptyArray()

                if (elements.isEmpty()) {
                    return ExtractMethodResult(
                        success = false,
                        message = "No PSI elements found in selected lines",
                        file = filePath,
                        newMethodName = newMethodName,
                        startLine = startLine,
                        endLine = endLine
                    )
                }

                val targetClass = resolveTargetClass(psiFile, startOffset, endOffset, elements.toList())
                    ?: return ExtractMethodResult(
                        success = false,
                        message = "Unable to resolve target class for extract method",
                        file = filePath,
                        newMethodName = newMethodName,
                        startLine = startLine,
                        endLine = endLine
                    )

                var success = false
                var failureMessage: String? = null

                val extractionAction = Runnable {
                    try {
                        val processor = ExtractMethodHandler.getProcessor(project, elements, psiFile, false)
                        if (processor == null) {
                            failureMessage = "Extract method processor could not be created"
                            return@Runnable
                        }

                        processor.setTargetClass(targetClass)
                        processor.setMethodName(newMethodName)
                        processor.setShowErrorDialogs(false)

                        if (processor.prepare()) {
                            processor.setMethodName(newMethodName)
                            ExtractMethodHandler.extractMethod(project, processor)
                            success = true
                        } else {
                            failureMessage = "Extract method validation failed"
                        }
                    } catch (e: Throwable) {
                        failureMessage = e.message ?: e.javaClass.simpleName
                    }
                }

                WriteCommandAction.runWriteCommandAction(project, "Extract Method '$newMethodName'", null, extractionAction)

                return if (success) {
                    ExtractMethodResult(
                        success = true,
                        message = "Successfully extracted method '$newMethodName'",
                        file = filePath,
                        newMethodName = newMethodName,
                        startLine = startLine,
                        endLine = endLine,
                        affectedFiles = listOf(filePath)
                    )
                } else {
                    ExtractMethodResult(
                        success = false,
                        message = failureMessage ?: "Extract method validation failed",
                        file = filePath,
                        newMethodName = newMethodName,
                        startLine = startLine,
                        endLine = endLine
                    )
                }
            } finally {
                if (editorHandle.ownsEditor) {
                    EditorFactory.getInstance().releaseEditor(editor)
                }
            }
        } catch (e: Throwable) {
            return ExtractMethodResult(
                success = false,
                message = "Extract failed: ${e.message}",
                file = filePath,
                newMethodName = newMethodName,
                startLine = startLine,
                endLine = endLine
            )
        }
    }

    private fun collectSelectedElements(psiFile: PsiFile, startOffset: Int, endOffset: Int): List<PsiElement> {
        val startElement = psiFile.findElementAt(startOffset) ?: return emptyList()
        val endElement = psiFile.findElementAt((endOffset - 1).coerceAtLeast(startOffset)) ?: return emptyList()

        val commonParent = PsiTreeUtil.findCommonParent(startElement, endElement) ?: startElement
        val directChildren = commonParent.children
            .filter { child ->
                val range = child.textRange
                child !is PsiFile &&
                    child !is PsiWhiteSpace &&
                    range != null &&
                    range.startOffset >= startOffset &&
                    range.endOffset <= endOffset
            }

        if (directChildren.isNotEmpty()) {
            return directChildren.sortedBy { it.textRange?.startOffset ?: Int.MAX_VALUE }
        }

        val candidates = PsiTreeUtil.collectElements(psiFile) { element ->
            val range = element.textRange
            element !is PsiFile &&
                element !is PsiWhiteSpace &&
                range != null &&
                range.startOffset >= startOffset &&
                range.endOffset <= endOffset
        }.toList()

        return candidates
            .filter { candidate ->
                val candidateRange = candidate.textRange ?: return@filter false
                candidates.none { other ->
                    if (other == candidate) return@none false
                    val otherRange = other.textRange ?: return@none false
                    otherRange.startOffset <= candidateRange.startOffset && otherRange.endOffset >= candidateRange.endOffset
                }
            }
            .sortedBy { it.textRange?.startOffset ?: Int.MAX_VALUE }
    }

    private fun openEditor(virtualFile: com.intellij.openapi.vfs.VirtualFile, document: com.intellij.openapi.editor.Document): EditorHandle {
        val fileEditor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, virtualFile), true)
        if (fileEditor != null) return EditorHandle(fileEditor, ownsEditor = false)

        return EditorHandle(EditorFactory.getInstance().createEditor(document, project), ownsEditor = true)
    }

    private data class EditorHandle(val editor: Editor, val ownsEditor: Boolean)

    private fun resolveTargetClass(
        psiFile: PsiFile,
        startOffset: Int,
        endOffset: Int,
        selectedElements: List<PsiElement>
    ): PsiClass? {
        val candidates = listOfNotNull(
            selectedElements.firstOrNull(),
            psiFile.findElementAt(startOffset),
            psiFile.findElementAt(endOffset)
        )

        for (candidate in candidates) {
            PsiTreeUtil.getParentOfType(candidate, PsiClass::class.java)?.let { return it }

            PsiTreeUtil.getParentOfType(candidate, KtClassOrObject::class.java)
                ?.toLightClass()
                ?.let { return it }
        }

        PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java)?.let { return it }
        PsiTreeUtil.findChildOfType(psiFile, KtClassOrObject::class.java)
            ?.toLightClass()
            ?.let { return it }

        return null
    }

    private fun extractKotlinFunction(
        ktFile: KtFile,
        virtualFile: com.intellij.openapi.vfs.VirtualFile,
        newMethodName: String,
        startLine: Int,
        endLine: Int
    ): ExtractMethodResult {
        val document = PsiDocumentManager.getInstance(project).getDocument(ktFile)
            ?: return ExtractMethodResult(
                success = false,
                message = "Cannot get document for file: ${virtualFile.path}",
                file = virtualFile.path,
                newMethodName = newMethodName,
                startLine = startLine,
                endLine = endLine
            )

        PsiDocumentManager.getInstance(project).commitDocument(document)
        val startOffset = document.getLineStartOffset(startLine - 1)
        val endOffset = document.getLineEndOffset(endLine - 1)

        if (startOffset >= endOffset) {
            return ExtractMethodResult(
                success = false,
                message = "Invalid selection range for lines $startLine..$endLine",
                file = virtualFile.path,
                newMethodName = newMethodName,
                startLine = startLine,
                endLine = endLine
            )
        }

        val editorHandle = openEditor(virtualFile, document)
        val editor = editorHandle.editor
        try {
            editor.selectionModel.setSelection(startOffset, endOffset)

            var success = false
            var failureMessage: String? = null

            val action = Runnable {
                try {
                    val helper = KotlinExtractFunctionHelper(newMethodName)
                    val handler = ExtractKotlinFunctionHandler(false, helper)
                    handler.selectElements(editor, ktFile) { elements, target ->
                        handler.doInvoke(editor, ktFile, elements, target)
                        success = true
                    }
                } catch (e: Throwable) {
                    failureMessage = e.message ?: e.javaClass.simpleName
                }
            }

            WriteCommandAction.runWriteCommandAction(project, "Extract Function '$newMethodName'", null, action)

            return if (success) {
                ExtractMethodResult(
                    success = true,
                    message = "Successfully extracted function '$newMethodName'",
                    file = virtualFile.path,
                    newMethodName = newMethodName,
                    startLine = startLine,
                    endLine = endLine,
                    affectedFiles = listOf(virtualFile.path)
                )
            } else {
                ExtractMethodResult(
                    success = false,
                    message = failureMessage ?: "Extract function validation failed",
                    file = virtualFile.path,
                    newMethodName = newMethodName,
                    startLine = startLine,
                    endLine = endLine
                )
            }
        } finally {
            if (editorHandle.ownsEditor) {
                EditorFactory.getInstance().releaseEditor(editor)
            }
        }
    }
}
