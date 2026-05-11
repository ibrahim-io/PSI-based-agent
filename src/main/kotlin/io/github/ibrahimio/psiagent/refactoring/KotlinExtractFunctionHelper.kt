package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionTarget
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractableCodeDescriptorWithConflicts

class KotlinExtractFunctionHelper(private val methodName: String) : ExtractionEngineHelper("Extract Function") {

    override fun configureAndRun(
        project: Project,
        editor: Editor,
        descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
        onFinish: (ExtractionResult) -> Unit
    ) {
        val options = ExtractionGeneratorOptions(
            false,
            ExtractionTarget.FUNCTION,
            methodName,
            false,
            false,
            false
        )
        val configuration = ExtractionGeneratorConfiguration(descriptorWithConflicts.descriptor, options)
        doRefactor(configuration, onFinish)
    }
}
