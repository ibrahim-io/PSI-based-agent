package io.github.ibrahimio.psiagent.search

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsiCodeSearcherTest : BasePlatformTestCase() {

    fun testSearchAllIncludesFields() {
        val javaCode = """
            public class Config {
                private String apiKey = "secret";

                public String getApiKey() {
                    return apiKey;
                }
            }
        """.trimIndent()

        myFixture.configureByText("Config.java", javaCode)

        val searcher = PsiCodeSearcher(project)
        val results = searcher.searchAll("apiKey")

        assertFalse("Search should return results", results.isEmpty())
        assertTrue("Should include a field result", results.any { it.elementType == "FIELD" && it.name == "apiKey" })
    }

    fun testFindUsagesForFieldBySymbolType() {
        val javaCode = """
            public class Config {
                private String apiKey = "secret";

                public String getApiKey() {
                    return apiKey;
                }
            }
        """.trimIndent()

        myFixture.configureByText("Config.java", javaCode)

        val searcher = PsiCodeSearcher(project)
        val usages = searcher.findUsages("apiKey", className = "Config", symbolType = "field")

        assertFalse("Field usages should be found", usages.isEmpty())
        assertTrue("Usages should reference apiKey", usages.any { it.codeSnippet.contains("apiKey") })
    }
}


