package io.github.ibrahimio.psiagent.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile


data class MoveClassResult(
    val success: Boolean,
    val message: String,
    val file: String,
    val targetPackage: String,
    val className: String,
    val affectedFiles: List<String> = emptyList()
)

class MoveClassProcessor(private val project: Project) {

    fun moveClass(filePath: String, targetPackageName: String): MoveClassResult {
        if (targetPackageName.isBlank()) {
            return MoveClassResult(
                success = false,
                message = "Target package cannot be blank",
                file = filePath,
                targetPackage = targetPackageName,
                className = ""
            )
        }

        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
            ?: VirtualFileManager.getInstance().findFileByUrl(filePath)
            ?: LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return MoveClassResult(
                success = false,
                message = "File not found: $filePath",
                file = filePath,
                targetPackage = targetPackageName,
                className = ""
            )

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            ?: return MoveClassResult(
                success = false,
                message = "Cannot parse file: $filePath",
                file = filePath,
                targetPackage = targetPackageName,
                className = ""
            )

        val psiClass = resolveClass(psiFile)
            ?: return MoveClassResult(
                success = false,
                message = "No class found in file: $filePath",
                file = filePath,
                targetPackage = targetPackageName,
                className = ""
            )

        val className = psiClass.name ?: "<anonymous>"
        val sourceRoot = ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(virtualFile)
            ?: return MoveClassResult(
                success = false,
                message = "Unable to resolve source root for: $filePath",
                file = filePath,
                targetPackage = targetPackageName,
                className = className
            )

        val affectedFiles = linkedSetOf(filePath)

        return try {
            var targetDirectoryPath: String? = null
            var failureMessage: String? = null
            WriteCommandAction.runWriteCommandAction(project) {
                val targetDirectoryVFile = VfsUtil.createDirectoryIfMissing(
                    sourceRoot,
                    targetPackageName.replace('.', '/')
                ) ?: return@runWriteCommandAction run {
                    failureMessage = "Unable to resolve or create target package directory: $targetPackageName"
                    Unit
                }

                val targetDirectory = PsiManager.getInstance(project).findDirectory(targetDirectoryVFile)
                    ?: return@runWriteCommandAction run {
                        failureMessage = "Unable to convert target package directory to PSI: $targetPackageName"
                        Unit
                    }

                targetDirectoryPath = targetDirectory.virtualFile.path

                val processor = MoveFilesOrDirectoriesProcessor(
                    project,
                    arrayOf(psiFile),
                    targetDirectory,
                    false,
                    false,
                    null,
                    Runnable { }
                )
                processor.run()
            }

            if (failureMessage != null) {
                return MoveClassResult(
                    success = false,
                    message = failureMessage!!,
                    file = filePath,
                    targetPackage = targetPackageName,
                    className = className,
                    affectedFiles = affectedFiles.toList()
                )
            }

            if (targetDirectoryPath != null) {
                affectedFiles.add(targetDirectoryPath!!)
            }

            val movedClass = JavaPsiFacade.getInstance(project)
                .findClass("$targetPackageName.$className", com.intellij.psi.search.GlobalSearchScope.projectScope(project))

            movedClass?.containingFile?.virtualFile?.path?.let { affectedFiles.add(it) }

            MoveClassResult(
                success = true,
                message = "Successfully moved '$className' to package '$targetPackageName'",
                file = filePath,
                targetPackage = targetPackageName,
                className = className,
                affectedFiles = affectedFiles.toList()
            )
        } catch (e: Exception) {
            MoveClassResult(
                success = false,
                message = "Move failed: ${e.message}",
                file = filePath,
                targetPackage = targetPackageName,
                className = className,
                affectedFiles = affectedFiles.toList()
            )
        }
    }

    private fun resolveClass(psiFile: PsiFile): PsiClass? {
        PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
            .firstOrNull { it.containingClass == null }
            ?.let { return it }

        if (psiFile is KtFile) {
            PsiTreeUtil.findChildrenOfType(psiFile, KtClassOrObject::class.java)
                .firstOrNull { it.isTopLevel() }
                ?.toLightClass()
                ?.let { return it }
        }

        return null
    }
}







