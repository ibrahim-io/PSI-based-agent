package io.github.ibrahimio.psiagent.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MethodTargetResolverTest : BasePlatformTestCase() {

    fun testResolvesJavaMethodFromUsageSite() {
        val javaCode = """
            public class Calculator {
                public int add(int a, int b) {
                    return a + b;
                }

                public int compute() {
                    return <caret>add(1, 2);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.java", javaCode)
        val resolver = MethodTargetResolver()

        val method = resolver.resolveMethodAtCaret(psiFile, myFixture.caretOffset)

        assertNotNull(method)
        assertEquals("add", method!!.name)
    }

    fun testResolvesKotlinFunctionFromUsageSite() {
        val kotlinCode = """
            class Greeter {
                fun greet(name: String): String {
                    return "Hello, ${'$'}name"
                }

                fun use(): String {
                    return <caret>greet("Ibrahim")
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Greeter.kt", kotlinCode)
        val resolver = MethodTargetResolver()

        val method = resolver.resolveMethodAtCaret(psiFile, myFixture.caretOffset)

        assertNotNull(method)
        assertEquals("greet", method!!.name)
    }

    fun testResolvesJavaClassFromCaret() {
        val javaCode = """
            public class <caret>Calculator {
                public int add(int a, int b) {
                    return a + b;
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.java", javaCode)
        val resolver = MethodTargetResolver()

        val symbol = resolver.resolveSymbolAtCaret(psiFile, myFixture.caretOffset)

        assertNotNull(symbol)
        assertEquals("Calculator", symbol!!.name)
    }
}



