package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo
import org.jetbrains.kotlin.psi.KtFile


data class ChangeSignatureParameter(
    val type: String,
    val name: String,
    val defaultValue: String = ""
)

data class ChangeSignatureResult(
    val success: Boolean,
    val message: String,
    val file: String,
    val methodName: String,
    val newReturnType: String,
    val newParameters: List<ChangeSignatureParameter> = emptyList(),
    val affectedFiles: List<String> = emptyList()
)

class ChangeSignatureProcessor(private val project: Project) {

    fun changeSignature(
        filePath: String,
        methodName: String,
        newReturnType: String,
        newParameters: List<ChangeSignatureParameter>
    ): ChangeSignatureResult {
        if (methodName.isBlank()) {
            return ChangeSignatureResult(
                success = false,
                message = "Method name cannot be blank",
                file = filePath,
                methodName = methodName,
                newReturnType = newReturnType,
                newParameters = newParameters
            )
        }

        if (newReturnType.isBlank()) {
            return ChangeSignatureResult(
                success = false,
                message = "Return type cannot be blank",
                file = filePath,
                methodName = methodName,
                newReturnType = newReturnType,
                newParameters = newParameters
            )
        }

        val invalidParameter = newParameters.firstOrNull { it.type.isBlank() || it.name.isBlank() }
        if (invalidParameter != null) {
            return ChangeSignatureResult(
                success = false,
                message = "Parameter type and name cannot be blank",
                file = filePath,
                methodName = methodName,
                newReturnType = newReturnType,
                newParameters = newParameters
            )
        }

        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
            ?: VirtualFileManager.getInstance().findFileByUrl(filePath)
            ?: LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return ChangeSignatureResult(
                success = false,
                message = "File not found: $filePath",
                file = filePath,
                methodName = methodName,
                newReturnType = newReturnType,
                newParameters = newParameters
            )

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            ?: return ChangeSignatureResult(
                success = false,
                message = "Cannot parse file: $filePath",
                file = filePath,
                methodName = methodName,
                newReturnType = newReturnType,
                newParameters = newParameters
            )

        if (psiFile is KtFile) {
            return ChangeSignatureResult(
                success = false,
                message = "Kotlin change-signature is not supported in this prototype yet",
                file = filePath,
                methodName = methodName,
                newReturnType = newReturnType,
                newParameters = newParameters
            )
        }

        val method = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            .firstOrNull { it.name == methodName }
            ?: return ChangeSignatureResult(
                success = false,
                message = "Method '$methodName' not found in $filePath",
                file = filePath,
                methodName = methodName,
                newReturnType = newReturnType,
                newParameters = newParameters
            )

        val elementFactory = JavaPsiFacade.getElementFactory(project)
        val returnType = try {
            elementFactory.createTypeFromText(newReturnType, method)
        } catch (e: Exception) {
            return ChangeSignatureResult(
                success = false,
                message = "Invalid return type '$newReturnType': ${e.message}",
                file = filePath,
                methodName = methodName,
                newReturnType = newReturnType,
                newParameters = newParameters
            )
        }

        val oldParameters = method.parameterList.parameters.toList()
        val usedOldIndexes = mutableSetOf<Int>()
        val allowIndexFallback = newParameters.size == oldParameters.size
        val parameterInfos = newParameters.mapIndexed { index, parameter ->
            val oldIndex = matchOldParameterIndex(parameter, index, oldParameters, usedOldIndexes, allowIndexFallback)
            if (oldIndex >= 0) {
                usedOldIndexes.add(oldIndex)
            }
            val parameterType = elementFactory.createTypeFromText(parameter.type, method)
            ParameterInfoImpl(oldIndex, parameter.name, parameterType, parameter.defaultValue, false).apply {
                oldParameterIndex = oldIndex
                setDefaultValue(parameter.defaultValue)
            }
        }.toTypedArray()

        val affectedFiles = linkedSetOf(filePath)

        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                val processor = com.intellij.refactoring.changeSignature.ChangeSignatureProcessor(
                    project,
                    method,
                    false,
                    null,
                    methodName,
                    returnType,
                    parameterInfos,
                    emptyArray<ThrownExceptionInfo>()
                )
                processor.run()
            }

            ChangeSignatureResult(
                success = true,
                message = "Successfully changed signature of '$methodName'",
                file = filePath,
                methodName = methodName,
                newReturnType = newReturnType,
                newParameters = newParameters,
                affectedFiles = affectedFiles.toList()
            )
        } catch (e: Exception) {
            ChangeSignatureResult(
                success = false,
                message = "Change signature failed: ${e.message}",
                file = filePath,
                methodName = methodName,
                newReturnType = newReturnType,
                newParameters = newParameters,
                affectedFiles = affectedFiles.toList()
            )
        }
    }

    private fun matchOldParameterIndex(
        newParameter: ChangeSignatureParameter,
        newIndex: Int,
        oldParameters: List<com.intellij.psi.PsiParameter>,
        usedOldIndexes: Set<Int>,
        allowIndexFallback: Boolean
    ): Int {
        val byName = oldParameters.indexOfFirstIndexed { idx, old ->
            old.name == newParameter.name && idx !in usedOldIndexes
        }
        if (byName >= 0) {
            return byName
        }

        if (allowIndexFallback && newIndex in oldParameters.indices) {
            val oldAtIndex = oldParameters[newIndex]
            val sameType = oldAtIndex.type.canonicalText == newParameter.type
            if (newIndex !in usedOldIndexes && sameType) {
                return newIndex
            }
        }

        return -1
    }

    private inline fun <T> List<T>.indexOfFirstIndexed(predicate: (Int, T) -> Boolean): Int {
        for (i in indices) {
            if (predicate(i, this[i])) return i
        }
        return -1
    }
}






