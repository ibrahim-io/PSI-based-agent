package io.github.ibrahimio.psiagent.visualization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import java.util.concurrent.CopyOnWriteArrayList

fun interface PsiChangeListener {
    fun onChange(record: PsiChangeRecord)
}

@Service(Service.Level.PROJECT)
class PsiDiffService {

    private val listeners = CopyOnWriteArrayList<PsiChangeListener>()
    @Volatile
    private var latestRecord: PsiChangeRecord? = null

    fun publish(record: PsiChangeRecord) {
        latestRecord = record
        ApplicationManager.getApplication().invokeLater {
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


