package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil

data class CreateFieldResult(
    val success: Boolean,
    val message: String,
    val file: String,
    val fieldName: String,
    val fieldType: String,
    val affectedFiles: List<String> = emptyList()
)

/**
 * Creates a new field in a Java class.
 * Parameters:
 *   - file: path to .java file
 *   - className: name of the class where the field will be added (must exist)
 *   - fieldName: name of the new field
 *   - fieldType: type of the field (e.g., "String", "int", "List<String>")
 *   - initialValue: optional initial value (e.g., "null", "0", "new ArrayList<>()")
 *   - visibility: access level (public, private, protected, internal)
 *   - isStatic: if true, field is marked as static
 *   - isFinal: if true, field is marked as final
 */
class CreateFieldProcessor(private val project: Project) {

    fun createField(
        filePath: String,
        className: String,
        fieldName: String,
        fieldType: String,
        initialValue: String = "",
        visibility: String = "private",
        isStatic: Boolean = false,
        isFinal: Boolean = false
    ): CreateFieldResult {
        if (className.isBlank() || fieldName.isBlank() || fieldType.isBlank()) {
            return CreateFieldResult(
                success = false,
                message = "className, fieldName, and fieldType cannot be blank",
                file = filePath,
                fieldName = fieldName,
                fieldType = fieldType,
                affectedFiles = listOf(filePath)
            )
        }

        if (filePath.endsWith(".kt")) {
            return CreateFieldResult(
                success = false,
                message = "Kotlin field creation is not yet supported. Please use Java files for now.",
                file = filePath,
                fieldName = fieldName,
                fieldType = fieldType,
                affectedFiles = listOf(filePath)
            )
        }

        return try {
            val vfs = LocalFileSystem.getInstance().findFileByPath(filePath)
                ?: return CreateFieldResult(
                    success = false,
                    message = "File not found: $filePath",
                    file = filePath,
                    fieldName = fieldName,
                    fieldType = fieldType,
                    affectedFiles = listOf(filePath)
                )

            val psiFile = PsiManager.getInstance(project).findFile(vfs)
                ?: return CreateFieldResult(
                    success = false,
                    message = "Could not parse file: $filePath",
                    file = filePath,
                    fieldName = fieldName,
                    fieldType = fieldType,
                    affectedFiles = listOf(filePath)
                )

            val targetClass = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
                .firstOrNull { it.name == className }
                ?: return CreateFieldResult(
                    success = false,
                    message = "Class '$className' not found in $filePath",
                    file = filePath,
                    fieldName = fieldName,
                    fieldType = fieldType,
                    affectedFiles = listOf(filePath)
                )

            val fieldCode = generateFieldCode(
                fieldName = fieldName,
                fieldType = fieldType,
                initialValue = initialValue,
                visibility = visibility,
                isStatic = isStatic,
                isFinal = isFinal
            )

            WriteCommandAction.runWriteCommandAction(project) {
                val factory = JavaPsiFacade.getInstance(project).elementFactory
                val field = factory.createFieldFromText(fieldCode, targetClass)
                targetClass.add(field)
            }

            CreateFieldResult(
                success = true,
                message = "Field '$fieldName' of type '$fieldType' created successfully in class '$className'",
                file = filePath,
                fieldName = fieldName,
                fieldType = fieldType,
                affectedFiles = listOf(filePath)
            )
        } catch (e: Exception) {
            CreateFieldResult(
                success = false,
                message = "Error creating field: ${e.message}",
                file = filePath,
                fieldName = fieldName,
                fieldType = fieldType,
                affectedFiles = listOf(filePath)
            )
        }
    }

    private fun generateFieldCode(
        fieldName: String,
        fieldType: String,
        initialValue: String,
        visibility: String,
        isStatic: Boolean,
        isFinal: Boolean
    ): String {
        val staticMod = if (isStatic) "static " else ""
        val finalMod = if (isFinal) "final " else ""
        val assignPart = if (initialValue.isNotBlank()) " = $initialValue" else ""

        return "$visibility $staticMod$finalMod$fieldType $fieldName$assignPart;"
    }
}


