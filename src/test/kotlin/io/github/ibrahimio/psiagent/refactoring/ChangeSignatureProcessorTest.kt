package io.github.ibrahimio.psiagent.refactoring

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ChangeSignatureProcessorTest : BasePlatformTestCase() {

    fun testChangeSignatureUpdatesJavaDeclarationAndCallSites() {
        val javaCode = """
            public class Calculator {
                public int sum(int a, int b) {
                    return a + b;
                }

                public int use() {
                    return sum(1, 2);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.java", javaCode)
        val processor = ChangeSignatureProcessor(project)

        val result = processor.changeSignature(
            filePath = psiFile.virtualFile.url,
            methodName = "sum",
            newReturnType = "long",
            newParameters = listOf(
                ChangeSignatureParameter(type = "int", name = "a"),
                ChangeSignatureParameter(type = "int", name = "b"),
                ChangeSignatureParameter(type = "int", name = "extra", defaultValue = "0")
            )
        )

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertTrue("Change signature should succeed", result.success)
        assertTrue("Method signature should be updated", psiFile.text.contains("long sum(int a, int b, int extra)"))
        assertTrue("Call site should include default value for new parameter", psiFile.text.contains("sum(1, 2, 0)"))
    }

    fun testChangeSignatureRejectsBlankReturnType() {
        val javaCode = """
            public class Calculator {
                public int sum(int a, int b) {
                    return a + b;
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.java", javaCode)
        val processor = ChangeSignatureProcessor(project)

        val result = processor.changeSignature(
            filePath = psiFile.virtualFile.url,
            methodName = "sum",
            newReturnType = "",
            newParameters = listOf(
                ChangeSignatureParameter(type = "int", name = "a"),
                ChangeSignatureParameter(type = "int", name = "b")
            )
        )

        assertFalse("Change signature should fail for blank return type", result.success)
        assertTrue("Failure message should mention return type", result.message.contains("Return type", ignoreCase = true))
    }

    fun testChangeSignatureRejectsKotlinInPrototype() {
        val kotlinCode = """
            class Calculator {
                fun sum(a: Int, b: Int): Int {
                    return a + b
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.kt", kotlinCode)
        val processor = ChangeSignatureProcessor(project)

        val result = processor.changeSignature(
            filePath = psiFile.virtualFile.url,
            methodName = "sum",
            newReturnType = "Long",
            newParameters = listOf(
                ChangeSignatureParameter(type = "Int", name = "a"),
                ChangeSignatureParameter(type = "Int", name = "b")
            )
        )

        assertFalse("Kotlin should be rejected in this conservative prototype", result.success)
        assertTrue("Failure should mention Kotlin support", result.message.contains("Kotlin", ignoreCase = true))
    }

    fun testChangeSignatureReordersParametersByName() {
        val javaCode = """
            public class Calculator {
                public int sum(int a, int b) {
                    return a + b;
                }

                public int use() {
                    return sum(1, 2);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.java", javaCode)
        val processor = ChangeSignatureProcessor(project)

        val result = processor.changeSignature(
            filePath = psiFile.virtualFile.url,
            methodName = "sum",
            newReturnType = "int",
            newParameters = listOf(
                ChangeSignatureParameter(type = "int", name = "b"),
                ChangeSignatureParameter(type = "int", name = "a")
            )
        )

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertTrue("Change signature should succeed", result.success)
        assertTrue("Method declaration should be reordered", psiFile.text.contains("sum(int b, int a)"))
        assertTrue("Call site should reorder arguments", psiFile.text.contains("sum(2, 1)"))
    }

    fun testChangeSignaturePrependsNewParameterWithDefault() {
        val javaCode = """
            public class Calculator {
                public int sum(int a, int b) {
                    return a + b;
                }

                public int use() {
                    return sum(1, 2);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.java", javaCode)
        val processor = ChangeSignatureProcessor(project)

        val result = processor.changeSignature(
            filePath = psiFile.virtualFile.url,
            methodName = "sum",
            newReturnType = "int",
            newParameters = listOf(
                ChangeSignatureParameter(type = "int", name = "extra", defaultValue = "0"),
                ChangeSignatureParameter(type = "int", name = "a"),
                ChangeSignatureParameter(type = "int", name = "b")
            )
        )

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertTrue("Change signature should succeed", result.success)
        assertTrue("Method declaration should include prepended parameter", psiFile.text.contains("sum(int extra, int a, int b)"))
        assertTrue("Call site should include prepended default", psiFile.text.contains("sum(0, 1, 2)"))
    }
}

