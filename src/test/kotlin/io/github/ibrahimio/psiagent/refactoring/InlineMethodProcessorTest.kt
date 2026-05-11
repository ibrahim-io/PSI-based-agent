package io.github.ibrahimio.psiagent.refactoring

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class InlineMethodProcessorTest : BasePlatformTestCase() {

    fun testInlineJavaMethodSucceeds() {
        val javaCode = """
            public class Greeter {
                private String greeting() {
                    return "hello";
                }

                public String say() {
                    return greeting();
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Greeter.java", javaCode)
        val processor = InlineMethodProcessor(project)

        val result = processor.inlineMethod(psiFile.virtualFile.url, "greeting")
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertTrue("Inline should succeed", result.success)
        assertFalse("Original call should be removed", psiFile.text.contains("greeting()"))
        assertTrue("Inlined literal should appear in the file", psiFile.text.contains("return \"hello\";"))
    }

    fun testInlineKotlinFunctionSucceeds() {
        val kotlinCode = """
            class Greeter {
                private fun greeting() = "hello"

                fun say() = greeting()
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Greeter.kt", kotlinCode)
        val processor = InlineMethodProcessor(project)

        val result = processor.inlineMethod(psiFile.virtualFile.url, "greeting")
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertTrue("Inline should succeed", result.success)
        assertFalse("Original call should be removed", psiFile.text.contains("greeting()"))
        assertTrue("Inlined expression should appear in the file", psiFile.text.contains("fun say() = \"hello\""))
    }

    fun testInlineMethodRejectsUnsupportedParameters() {
        val javaCode = """
            public class Greeter {
                private String greeting(String name) {
                    return name;
                }

                public String say() {
                    return greeting("world");
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Greeter.java", javaCode)
        val processor = InlineMethodProcessor(project)

        val result = processor.inlineMethod(psiFile.virtualFile.url, "greeting")

        assertFalse("Inline should fail for parameterized methods in this prototype", result.success)
        assertTrue("Failure should mention the target was not found or unsupported", result.message.isNotBlank())
    }
}




