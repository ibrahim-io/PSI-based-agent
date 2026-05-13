package io.github.ibrahimio.psiagent.refactoring

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MethodExtractorTest : BasePlatformTestCase() {

    fun testRejectsBlankMethodNameBeforePsiWork() {
        val extractor = MethodExtractor(project)

        val result = extractor.extractMethod("src/Main.java", "   ", 1, 1)

        assertFalse("Blank method names should be rejected", result.success)
        assertTrue("Failure should mention blank method name", result.message.contains("newMethodName cannot be blank"))
    }

    fun testRejectsInvalidLineRangeBeforePsiWork() {
        val extractor = MethodExtractor(project)

        val result = extractor.extractMethod("src/Main.java", "psi_print", 3, 2)

        assertFalse("Invalid line ranges should be rejected", result.success)
        assertTrue("Failure should mention invalid range", result.message.contains("Invalid line range"))
    }

    fun testExtractMethodFromJavaFile() {
        val javaCode = """
            package sample;

            public class Calculator {
                public void greet() {
                    System.out.println("Hello");
                    System.out.println("World");
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.java", javaCode)
        val extractor = MethodExtractor(project)

        val result = extractor.extractMethod(psiFile.virtualFile.url, "printGreeting", 5, 6)

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertFalse("Extract method should be blocked in unit test mode", result.success)
        assertTrue("Failure should mention unit test mode", result.message.contains("unit test mode"))
    }

    fun testExtractMethodFromKotlinFile() {
        val kotlinCode = """
            package sample

            class Calculator {
                fun greet() {
                    println("Hello")
                    println("World")
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.kt", kotlinCode)
        val extractor = MethodExtractor(project)

        val result = extractor.extractMethod(psiFile.virtualFile.url, "printGreeting", 5, 6)

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertFalse("Extract method should be blocked in unit test mode", result.success)
        assertTrue("Failure should mention unit test mode", result.message.contains("unit test mode"))
    }
}