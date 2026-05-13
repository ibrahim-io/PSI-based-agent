package io.github.ibrahimio.psiagent.refactoring

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class IntroduceVariableProcessorTest : BasePlatformTestCase() {

    fun testIntroduceVariableSucceedsForJavaExpression() {
        val javaCode = """
            public class Calculator {
                public int compute() {
                    return 1 + 2;
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.java", javaCode)
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
        val expressionRange = rangeForText(document, psiFile.text)
        val processor = IntroduceVariableProcessor(project)

        val result = processor.introduceVariable(
            psiFile.virtualFile.url,
            "sum",
            expressionRange.startLine,
            expressionRange.startColumn,
            expressionRange.endLine,
            expressionRange.endColumn
        )

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertTrue("Introduce variable should succeed", result.success)
        assertTrue("Declaration should be introduced", psiFile.text.contains("var sum = 1 + 2;"))
        assertTrue("Original expression should be replaced", psiFile.text.contains("return sum;"))
    }

    fun testIntroduceVariableSucceedsForKotlinExpression() {
        val kotlinCode = """
            class Calculator {
                fun compute(): Int {
                    return 1 + 2
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.kt", kotlinCode)
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
        val expressionRange = rangeForText(document, psiFile.text)
        val processor = IntroduceVariableProcessor(project)

        val result = processor.introduceVariable(
            psiFile.virtualFile.url,
            "sum",
            expressionRange.startLine,
            expressionRange.startColumn,
            expressionRange.endLine,
            expressionRange.endColumn
        )

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertTrue("Introduce variable should succeed", result.success)
        assertTrue("Declaration should be introduced", psiFile.text.contains("val sum = 1 + 2"))
        assertTrue("Original expression should be replaced", psiFile.text.contains("return sum"))
    }

    fun testIntroduceVariableRejectsBlankVariableName() {
        val javaCode = """
            public class Calculator {
                public int compute() {
                    return 1 + 2;
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.java", javaCode)
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
        val expressionRange = rangeForText(document, psiFile.text)
        val processor = IntroduceVariableProcessor(project)

        val result = processor.introduceVariable(
            psiFile.virtualFile.url,
            "",
            expressionRange.startLine,
            expressionRange.startColumn,
            expressionRange.endLine,
            expressionRange.endColumn
        )

        assertFalse("Introduce variable should fail for blank names", result.success)
        assertTrue("Failure should explain the invalid variable name", result.message.contains("blank", ignoreCase = true))
    }

    private data class TextRangeLocation(
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int
    )

    private fun rangeForText(document: com.intellij.openapi.editor.Document, text: String): TextRangeLocation {
        val needle = "1 + 2"
        val startOffset = text.indexOf(needle)
        assertTrue("Needle '$needle' should be present in test text", startOffset >= 0)
        val endOffsetExclusive = startOffset + needle.length
        val startLine = document.getLineNumber(startOffset) + 1
        val endLine = document.getLineNumber(endOffsetExclusive - 1) + 1
        val startColumn = startOffset - document.getLineStartOffset(startLine - 1) + 1
        val endColumn = (endOffsetExclusive - 1) - document.getLineStartOffset(endLine - 1) + 1
        return TextRangeLocation(startLine, startColumn, endLine, endColumn)
    }
}

