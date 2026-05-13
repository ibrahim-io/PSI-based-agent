package io.github.ibrahimio.psiagent.visualization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.util.concurrent.CopyOnWriteArrayList

fun interface PsiChangeListener {
    fun onChange(record: PsiChangeRecord)
}

@Service(Service.Level.PROJECT)
class PsiDiffService(private val project: Project) {

    private val listeners = CopyOnWriteArrayList<PsiChangeListener>()
    @Volatile
    private var latestRecord: PsiChangeRecord? = null

    fun publish(record: PsiChangeRecord) {
        latestRecord = record
        ApplicationManager.getApplication().invokeLater {
            // show the PSI Change Preview tool window when a change is published
            try {
                val tw = ToolWindowManager.getInstance(project).getToolWindow("PSI Change Preview")
                tw?.show(null)
            } catch (e: Exception) {
                // best-effort: ignore if tool window cannot be shown
            }
            listeners.forEach { listener ->
                runCatching { listener.onChange(record) }
            }
        }
    }

    fun latest(): PsiChangeRecord? = latestRecord

    fun addListener(listener: PsiChangeListener) {
        listeners.add(listener)
        latestRecord?.let { record ->
            ApplicationManager.getApplication().invokeLater {
                runCatching { listener.onChange(record) }
            }
        }
    }

    fun removeListener(listener: PsiChangeListener) {
        listeners.remove(listener)
    }
}


