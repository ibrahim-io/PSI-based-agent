package io.github.ibrahimio.psiagent.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.ui.Messages
import io.github.ibrahimio.psiagent.refactoring.MethodRenamer
import io.github.ibrahimio.psiagent.visualization.PsiChangeVisualizer
import io.github.ibrahimio.psiagent.visualization.PsiChangeRecord
import io.github.ibrahimio.psiagent.visualization.PsiDiffService
import io.github.ibrahimio.psiagent.visualization.PsiSnapshotSerializer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

class RenameMethodAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val offset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(offset)

        val symbol = resolveSymbolAtCaret(elementAtCaret)

        val currentName = symbol?.name ?: run {
            Messages.showWarningDialog(project, "Place cursor on a class, method, field, property, or variable to rename it.", "No Symbol Selected")
            return
        }

        val newName = Messages.showInputDialog(
            project,
            "Enter new name for symbol '$currentName':",
            "Rename Symbol (PSI Agent)",
            Messages.getQuestionIcon(),
            currentName,
            null
        ) ?: return

        if (newName.isBlank() || newName == currentName) return

        val filePath = symbol.containingFile?.virtualFile?.path ?: return

        val beforeSnapshot = ReadAction.compute<io.github.ibrahimio.psiagent.visualization.PsiFileSnapshot?, RuntimeException> {
            PsiSnapshotSerializer(project).snapshot(filePath)
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val renamer = MethodRenamer(project)
            val result = renamer.renameSymbol(filePath, currentName, newName)
            val afterSnapshot = ReadAction.compute<io.github.ibrahimio.psiagent.visualization.PsiFileSnapshot?, RuntimeException> {
                PsiSnapshotSerializer(project).snapshot(filePath)
            }
            project.getService(PsiDiffService::class.java).publish(
                PsiChangeRecord(
                    toolName = "psi_rename",
                    filePath = filePath,
                    success = result.success,
                    message = result.message,
                    affectedFiles = result.affectedFiles,
                    before = beforeSnapshot,
                    after = afterSnapshot
                )
            )
            val visualizer = PsiChangeVisualizer()
            val report = visualizer.visualize(result)
            val reportText = visualizer.printReport(report)

            ApplicationManager.getApplication().invokeLater {
                showResult(project, result.success, result.message, reportText)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = editor != null && psiFile != null
    }

    private fun resolveSymbolAtCaret(elementAtCaret: com.intellij.psi.PsiElement?): PsiNamedElement? {
        val resolved = elementAtCaret?.parent?.reference?.resolve()
        when (resolved) {
            is PsiMethod, is PsiClass, is PsiField, is KtNamedFunction, is KtClassOrObject, is KtProperty -> {
                return resolved as PsiNamedElement
            }
        }

        return PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java)
            ?: PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass::class.java)
            ?: PsiTreeUtil.getParentOfType(elementAtCaret, PsiField::class.java)
            ?: PsiTreeUtil.getParentOfType(elementAtCaret, KtNamedFunction::class.java)
            ?: PsiTreeUtil.getParentOfType(elementAtCaret, KtClassOrObject::class.java)
            ?: PsiTreeUtil.getParentOfType(elementAtCaret, KtProperty::class.java)
    }

    private fun showResult(project: Project, success: Boolean, message: String, reportText: String) {
        if (success) {
            Messages.showInfoMessage(project, reportText, "PSI Agent — Rename Complete")
        } else {
            Messages.showErrorDialog(project, message, "PSI Agent — Rename Failed")
        }
    }
}
