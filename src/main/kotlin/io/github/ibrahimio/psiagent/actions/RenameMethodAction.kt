package io.github.ibrahimio.psiagent.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.project.Project
import io.github.ibrahimio.psiagent.refactoring.MethodRenamer
import io.github.ibrahimio.psiagent.visualization.PsiChangeVisualizer

class RenameMethodAction : AnAction() {

    private val targetResolver = MethodTargetResolver()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val offset = editor.caretModel.offset
        val method = targetResolver.resolveSymbolAtCaret(psiFile, offset)

        val currentName = method?.name ?: run {
            Messages.showWarningDialog(project, "Place cursor on a method, class, interface, or function to rename it.", "No Symbol Selected")
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

        // Use the resolved symbol's containing file — it may differ from the open file (cross-file reference)
        val filePath = method.containingFile?.virtualFile?.path ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val renamer = MethodRenamer(project)
            val result = renamer.renameSymbol(filePath, currentName, newName)
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
        e.presentation.isEnabledAndVisible =
            editor != null &&
                psiFile != null &&
                targetResolver.resolveSymbolAtCaret(psiFile, editor.caretModel.offset) != null
    }

    private fun showResult(project: Project, success: Boolean, message: String, reportText: String) {
        if (success) {
            Messages.showInfoMessage(project, reportText, "PSI Agent — Rename Complete")
        } else {
            Messages.showErrorDialog(project, message, "PSI Agent — Rename Failed")
        }
    }
}
