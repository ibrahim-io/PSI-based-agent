package io.github.ibrahimio.psiagent.refactoring

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MethodExtractorTest : BasePlatformTestCase() {

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
