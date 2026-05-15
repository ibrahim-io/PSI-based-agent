package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

data class CreateClassResult(
    val success: Boolean,
    val message: String,
    val file: String,
    val className: String,
    val affectedFiles: List<String> = emptyList()
)

/**
 * Creates a new class (Java only for now) in a file.
 * Parameters:
 *   - packageName: the package for the new class (e.g., "com.example")
 *   - className: name of the new class
 *   - extends: optional parent class (e.g., "ExtendsClass")
 *   - isInterface: if true, creates an interface instead of a class
 */
class CreateClassProcessor(private val project: Project) {

    fun createClass(
        filePath: String,
        packageName: String,
        className: String,
        extends: String = "",
        isInterface: Boolean = false,
        isKotlin: Boolean = false
    ): CreateClassResult {
        if (className.isBlank()) {
            return CreateClassResult(
                success = false,
                message = "className cannot be blank",
                file = filePath,
                className = className,
                affectedFiles = listOf(filePath)
            )
        }

        if (isKotlin) {
            return CreateClassResult(
                success = false,
                message = "Kotlin class creation is not yet supported. Please use Java files for now.",
                file = filePath,
                className = className,
                affectedFiles = listOf(filePath)
            )
        }

        return try {
            val vfs = LocalFileSystem.getInstance().findFileByPath(filePath)
                ?: return CreateClassResult(
                    success = false,
                    message = "File not found: $filePath",
                    file = filePath,
                    className = className,
                    affectedFiles = listOf(filePath)
                )

            val psiFile = PsiManager.getInstance(project).findFile(vfs)
                ?: return CreateClassResult(
                    success = false,
                    message = "Could not parse file: $filePath",
                    file = filePath,
                    className = className,
                    affectedFiles = listOf(filePath)
                )

            val classCode = generateClassCode(
                packageName = packageName,
                className = className,
                extends = extends,
                isInterface = isInterface
            )

            WriteCommandAction.runWriteCommandAction(project) {
                val factory = JavaPsiFacade.getInstance(project).elementFactory
                val newClass = factory.createClassFromText(classCode, psiFile)
                psiFile.add(newClass)
            }

            CreateClassResult(
                success = true,
                message = "${if (isInterface) "Interface" else "Class"} '$className' created successfully in package '$packageName'",
                file = filePath,
                className = className,
                affectedFiles = listOf(filePath)
            )
        } catch (e: Exception) {
            CreateClassResult(
                success = false,
                message = "Error creating class: ${e.message}",
                file = filePath,
                className = className,
                affectedFiles = listOf(filePath)
            )
        }
    }

    private fun generateClassCode(
        packageName: String,
        className: String,
        extends: String,
        isInterface: Boolean
    ): String {
        val keyword = if (isInterface) "interface" else "class"
        val extendsClause = if (extends.isNotBlank()) " extends $extends" else ""

        return buildString {
            if (packageName.isNotBlank()) {
                append("package $packageName;\n\n")
            }
            append("public $keyword $className$extendsClause {\n")
            if (!isInterface) {
                append("    // TODO: implement $className\n")
            }
            append("}")
        }
    }
}


