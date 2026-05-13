package io.github.ibrahimio.psiagent.refactoring

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DeleteSymbolProcessorTest : BasePlatformTestCase() {

    fun testDeleteJavaMethodFromFile() {
        val javaCode = """
            public class Calculator {
                public int keep() {
                    return 1;
                }

                public int removeMe() {
                    return 2;
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.java", javaCode)
        val processor = DeleteSymbolProcessor(project)

        val result = processor.deleteSymbol(psiFile.virtualFile.url, "removeMe", "method")

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertTrue("Delete should succeed", result.success)
        assertFalse("File should no longer contain the deleted method", psiFile.text.contains("removeMe"))
        assertTrue("Affected files should include the original file", result.affectedFiles.isNotEmpty())
    }

    fun testRejectsBlankSymbolName() {
        val processor = DeleteSymbolProcessor(project)

        val result = processor.deleteSymbol("src/Calculator.java", "   ", "method")

        assertFalse("Blank names should be rejected", result.success)
        assertTrue("Failure should mention blank symbol name", result.message.contains("blank"))
    }
}

