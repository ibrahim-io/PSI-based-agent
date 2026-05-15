package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile

data class CreateMethodResult(
    val success: Boolean,
    val message: String,
    val file: String,
    val methodName: String,
    val methodSignature: String,
    val affectedFiles: List<String> = emptyList()
)

/**
 * Creates a new method in a Java class.
 * Parameters:
 *   - file: path to .java file
 *   - className: name of the class where the method will be added (must exist)
 *   - methodName: name of the new method
 *   - returnType: return type (e.g., "void", "String", "int")
 *   - parameters: comma-separated list of parameters (e.g., "String name, int age")
 *   - methodBody: optional method body; if empty, generates simple placeholder
 */
class CreateMethodProcessor(private val project: Project) {

    fun createMethod(
        filePath: String,
        className: String,
        methodName: String,
        returnType: String = "void",
        parameters: String = "",
        methodBody: String = "",
        isStatic: Boolean = false,
        visibility: String = "public"
    ): CreateMethodResult {
        if (className.isBlank() || methodName.isBlank()) {
            return CreateMethodResult(
                success = false,
                message = "className and methodName cannot be blank",
                file = filePath,
                methodName = methodName,
                methodSignature = "",
                affectedFiles = listOf(filePath)
            )
        }

        if (filePath.endsWith(".kt")) {
            return CreateMethodResult(
                success = false,
                message = "Kotlin method creation is not yet supported. Please use Java files for now.",
                file = filePath,
                methodName = methodName,
                methodSignature = "",
                affectedFiles = listOf(filePath)
            )
        }

        return try {
            val vfs = LocalFileSystem.getInstance().findFileByPath(filePath)
                ?: return CreateMethodResult(
                    success = false,
                    message = "File not found: $filePath",
                    file = filePath,
                    methodName = methodName,
                    methodSignature = "",
                    affectedFiles = listOf(filePath)
                )

            val psiFile = PsiManager.getInstance(project).findFile(vfs)
                ?: return CreateMethodResult(
                    success = false,
                    message = "Could not parse file: $filePath",
                    file = filePath,
                    methodName = methodName,
                    methodSignature = "",
                    affectedFiles = listOf(filePath)
                )

            val targetClass = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
                .firstOrNull { it.name == className }
                ?: return CreateMethodResult(
                    success = false,
                    message = "Class '$className' not found in $filePath",
                    file = filePath,
                    methodName = methodName,
                    methodSignature = "",
                    affectedFiles = listOf(filePath)
                )

            val methodCode = generateMethodCode(
                methodName = methodName,
                returnType = returnType,
                parameters = parameters,
                methodBody = methodBody,
                isStatic = isStatic,
                visibility = visibility
            )

            val signature = "$visibility ${if (isStatic) "static " else ""}$returnType $methodName($parameters)"

            WriteCommandAction.runWriteCommandAction(project) {
                val factory = JavaPsiFacade.getInstance(project).elementFactory
                val psiMethod = factory.createMethodFromText(methodCode, targetClass)
                targetClass.add(psiMethod)
            }

            CreateMethodResult(
                success = true,
                message = "Method '$methodName' created successfully in class '$className'",
                file = filePath,
                methodName = methodName,
                methodSignature = signature,
                affectedFiles = listOf(filePath)
            )
        } catch (e: Exception) {
            CreateMethodResult(
                success = false,
                message = "Error creating method: ${e.message}",
                file = filePath,
                methodName = methodName,
                methodSignature = "",
                affectedFiles = listOf(filePath)
            )
        }
    }

    private fun generateMethodCode(
        methodName: String,
        returnType: String,
        parameters: String,
        methodBody: String,
        isStatic: Boolean,
        visibility: String
    ): String {
        val body = methodBody.ifBlank {
            if (returnType == "void") {
                "        // TODO: implement $methodName\n    "
            } else {
                "        // TODO: implement $methodName\n        return null;\n    "
            }
        }

        val staticMod = if (isStatic) "static " else ""
        return "$visibility ${staticMod}$returnType $methodName($parameters) {\n$body\n    }"
    }
}




