package io.github.ibrahimio.psiagent.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import io.github.ibrahimio.psiagent.refactoring.MethodRenamer
import io.github.ibrahimio.psiagent.visualization.PsiChangeVisualizer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

class RenameMethodAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val offset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(offset)
        val method = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java)

        val currentName = method?.name ?: run {
            Messages.showWarningDialog(project, "Place cursor on a method to rename it.", "No Method Selected")
            return
        }

        val newName = Messages.showInputDialog(
            project,
            "Enter new name for method '$currentName':",
            "Rename Method (PSI Agent)",
            Messages.getQuestionIcon(),
            currentName,
            null
        ) ?: return

        if (newName.isBlank() || newName == currentName) return

        val filePath = psiFile.virtualFile?.path ?: return
        // WriteCommandAction inside MethodRenamer handles its own write-thread synchronisation,
        // so it is safe to call from a pooled thread.
        ApplicationManager.getApplication().executeOnPooledThread {
            val renamer = MethodRenamer(project)
            val result = renamer.renameMethod(filePath, currentName, newName)
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

    private fun showResult(project: Project, success: Boolean, message: String, reportText: String) {
        if (success) {
            Messages.showInfoMessage(project, reportText, "PSI Agent — Rename Complete")
        } else {
            Messages.showErrorDialog(project, message, "PSI Agent — Rename Failed")
        }
    }
}
