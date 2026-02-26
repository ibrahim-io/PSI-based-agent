package io.github.ibrahimio.psiagent.search

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch

data class SearchResult(
    val elementType: String,
    val name: String,
    val qualifiedName: String?,
    val filePath: String,
    val lineNumber: Int,
    val isDefinition: Boolean,
    val codeSnippet: String
)

class PsiCodeSearcher(private val project: Project) {

    private val scope = GlobalSearchScope.projectScope(project)

    fun searchMethods(namePattern: String): List<SearchResult> {
        val cache = PsiShortNamesCache.getInstance(project)
        val regex = globToRegex(namePattern)
        val allMethodNames = cache.allMethodNames.filter { it.matches(regex) }
        return allMethodNames.flatMap { name ->
            cache.getMethodsByName(name, scope).map { method ->
                buildSearchResult(method, isDefinition = true)
            }
        }
    }

    fun searchClasses(namePattern: String): List<SearchResult> {
        val cache = PsiShortNamesCache.getInstance(project)
        val regex = globToRegex(namePattern)
        val allClassNames = cache.allClassNames.filter { it.matches(regex) }
        return allClassNames.flatMap { name ->
            cache.getClassesByName(name, scope).map { cls ->
                buildSearchResult(cls, isDefinition = true)
            }
        }
    }

    fun findUsages(methodName: String, className: String? = null): List<SearchResult> {
        val cache = PsiShortNamesCache.getInstance(project)
        val methods = cache.getMethodsByName(methodName, scope).filter { method ->
            className == null || method.containingClass?.name == className
        }
        return methods.flatMap { method ->
            MethodReferencesSearch.search(method, scope, true).findAll().map { ref ->
                buildSearchResult(ref.element, isDefinition = false)
            }
        }
    }

    fun searchAll(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        results += searchMethods(query)
        results += searchClasses(query)

        val cache = PsiShortNamesCache.getInstance(project)
        val regex = globToRegex(query)
        val fieldNames = cache.allFieldNames.filter { it.matches(regex) }
        fieldNames.flatMapTo(results) { name ->
            cache.getFieldsByName(name, scope).map { field ->
                buildSearchResult(field, isDefinition = true)
            }
        }

        return results.distinctBy { "${it.filePath}:${it.lineNumber}:${it.name}" }
    }

    private fun buildSearchResult(element: PsiElement, isDefinition: Boolean): SearchResult {
        val file = element.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        val lineNumber = document?.getLineNumber(element.textOffset)?.plus(1) ?: -1
        val filePath = file.virtualFile?.path ?: "<in-memory>"

        val (type, name, qualifiedName) = when (element) {
            is PsiMethod -> Triple(
                "METHOD",
                element.name,
                "${element.containingClass?.qualifiedName}.${element.name}"
            )
            is PsiClass -> Triple(
                "CLASS",
                element.name ?: "<anonymous>",
                element.qualifiedName
            )
            is PsiField -> Triple(
                "FIELD",
                element.name,
                "${element.containingClass?.qualifiedName}.${element.name}"
            )
            else -> Triple("ELEMENT", (element as? PsiNamedElement)?.name ?: element.javaClass.simpleName, null)
        }

        val snippet = element.text.lines().take(3).joinToString("\n").take(200)

        return SearchResult(
            elementType = type,
            name = name,
            qualifiedName = qualifiedName,
            filePath = filePath,
            lineNumber = lineNumber,
            isDefinition = isDefinition,
            codeSnippet = snippet
        )
    }

    private fun globToRegex(pattern: String): Regex {
        // If no wildcards, treat as substring match
        if (!pattern.contains('*') && !pattern.contains('?')) {
            return Regex(".*${Regex.escape(pattern)}.*", RegexOption.IGNORE_CASE)
        }
        val regexStr = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex(regexStr, RegexOption.IGNORE_CASE)
    }
}
