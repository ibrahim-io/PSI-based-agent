package io.github.ibrahimio.psiagent.refactoring

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue

class MethodRenamerTest : BasePlatformTestCase() {

    fun testRenameMethodSucceeds() {
        val javaCode = """
            public class Calculator {
                public int add(int a, int b) {
                    return a + b;
                }

                public int multiply(int a, int b) {
                    return a * b;
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Calculator.java", javaCode)
        val filePath = psiFile.virtualFile.url

        val renamer = MethodRenamer(project)
        val result = renamer.renameMethod(filePath, "add", "sum")

        assertTrue("Rename should succeed", result.success)
        assertEquals("sum", result.newName)
        assertEquals("add", result.oldName)
        assertFalse("Affected files should not be empty", result.affectedFiles.isEmpty())

        // Verify PSI was actually changed
        val renamedMethod = renamer.findMethodInFile(psiFile, "sum")
        assertNotNull("Renamed method 'sum' should exist in PSI", renamedMethod)

        val oldMethod = renamer.findMethodInFile(psiFile, "add")
        assertNull("Old method 'add' should no longer exist in PSI", oldMethod)
    }

    fun testRenameClassSucceeds() {
        val javaCode = """
            public class OldService {
                public String name() {
                    return "old";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("OldService.java", javaCode)
        val filePath = psiFile.virtualFile.url

        val renamer = MethodRenamer(project)
        val result = renamer.renameMethod(filePath, "OldService", "NewService")

        assertTrue("Class rename should succeed", result.success)
        assertNotNull("Renamed class should exist", renamer.findSymbolInFile(psiFile, "NewService"))
        assertNull("Old class should no longer exist", renamer.findSymbolInFile(psiFile, "OldService"))
        assertTrue("Updated text should contain new class name", psiFile.text.contains("class NewService"))
    }

    fun testRenameMethodNotFound() {
        val javaCode = """
            public class Greeter {
                public void greet(String name) {
                    System.out.println("Hello, " + name);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Greeter.java", javaCode)
        val filePath = psiFile.virtualFile.url

        val renamer = MethodRenamer(project)
        val result = renamer.renameMethod(filePath, "nonExistent", "newName")

        assertFalse("Rename should fail for non-existent method", result.success)
        assertTrue("Message should mention method name", result.message.contains("nonExistent"))
    }

    fun testRenameMethodFileNotFound() {
        val renamer = MethodRenamer(project)
        val result = renamer.renameMethod("/nonexistent/path/Foo.java", "foo", "bar")

        assertFalse("Rename should fail for missing file", result.success)
        assertTrue("Message should mention file path", result.message.contains("/nonexistent/path/Foo.java"))
    }

    fun testRenameMethodProducesPsiSnapshots() {
        val javaCode = """
            public class StringUtils {
                public String reverse(String input) {
                    return new StringBuilder(input).reverse().toString();
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("StringUtils.java", javaCode)
        val filePath = psiFile.virtualFile.url

        val renamer = MethodRenamer(project)
        val result = renamer.renameMethod(filePath, "reverse", "reverseString")

        assertTrue("Rename should succeed", result.success)
        assertFalse("Should have before snapshots", result.psiNodesBefore.isEmpty())
        assertFalse("Should have after snapshots", result.psiNodesAfter.isEmpty())
        assertEquals("Before snapshot should have old name", "reverse", result.psiNodesBefore[0].name)
        assertEquals("After snapshot should have new name", "reverseString", result.psiNodesAfter[0].name)
    }

    fun testRenameMethodWithUsagesAcrossMethods() {
        val javaCode = """
            public class MathHelper {
                public int compute(int x) {
                    return x * 2;
                }

                public int computeDouble(int x) {
                    return compute(x) + compute(x);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("MathHelper.java", javaCode)
        val filePath = psiFile.virtualFile.url

        val renamer = MethodRenamer(project)
        val result = renamer.renameMethod(filePath, "compute", "calculate")

        assertTrue("Rename should succeed", result.success)

        // Verify 'compute' is gone and 'calculate' exists
        assertNull("'compute' should no longer exist", renamer.findMethodInFile(psiFile, "compute"))
        assertNotNull("'calculate' should exist", renamer.findMethodInFile(psiFile, "calculate"))

        // Verify the call sites were also updated
        val fileText = psiFile.text
        assertFalse("Old name should not appear in file", fileText.contains("compute(x)"))
        assertTrue("New name should appear in call sites", fileText.contains("calculate(x)"))
    }
}
