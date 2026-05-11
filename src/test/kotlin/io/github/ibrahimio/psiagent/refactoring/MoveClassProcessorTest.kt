package io.github.ibrahimio.psiagent.refactoring

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MoveClassProcessorTest : BasePlatformTestCase() {

    fun testMoveJavaClassToNewPackage() {
        val javaCode = """
            package sample;

            public class Greeter {
                public String greet() {
                    return "hello";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Greeter.java", javaCode)
        val mover = MoveClassProcessor(project)

        val result = mover.moveClass(psiFile.virtualFile.url, "com.example.moved")

        assertTrue("Move should succeed", result.success)
        assertFalse("Affected files should not be empty", result.affectedFiles.isEmpty())

        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()

        val movedClass = JavaPsiFacade.getInstance(project)
            .findClass("com.example.moved.Greeter", GlobalSearchScope.projectScope(project))

        assertNotNull("Class should be found in the target package", movedClass)
        assertTrue("Result should mention the moved class", result.message.contains("Greeter"))
    }

    fun testMoveClassRejectsBlankTargetPackage() {
        val javaCode = """
            package sample;

            public class Greeter {
                public String greet() {
                    return "hello";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Greeter.java", javaCode)
        val mover = MoveClassProcessor(project)

        val result = mover.moveClass(psiFile.virtualFile.url, "")

        assertFalse("Move should fail for blank target package", result.success)
        assertTrue("Error should mention target package", result.message.contains("Target package cannot be blank"))
    }
}



