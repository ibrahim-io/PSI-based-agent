package io.github.ibrahimio.psiagent.actions

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtNamedFunction

class MethodTargetResolver {

    fun resolveMethodAtCaret(psiFile: PsiFile, offset: Int): PsiNamedElement? {
        return resolveSymbolAtCaret(psiFile, offset)
    }

    fun resolveSymbolAtCaret(psiFile: PsiFile, offset: Int): PsiNamedElement? {
        val elementAtCaret = psiFile.findElementAt(offset) ?: return null

        resolveFromReferences(elementAtCaret)?.let { return it }
        resolveFromReferences(elementAtCaret.parent)?.let { return it }

        return resolveFromTree(elementAtCaret)
    }

    private fun resolveFromReferences(start: PsiElement?): PsiNamedElement? {
        var current = start

        repeat(8) {
            val element = current ?: return null
            when (val resolved = element.reference?.resolve()) {
                is PsiMethod -> return resolved
                is PsiClass -> return resolved
                is KtNamedFunction -> return resolved
                is PsiNamedElement -> return resolved  // Catch-all for any other named elements
            }
            current = element.parent
        }

        return null
    }

    private fun resolveFromTree(elementAtCaret: PsiElement?): PsiNamedElement? {
        // Try to find parent elements of various types, from most specific to most general
        return PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java)
            ?: PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass::class.java)
            ?: PsiTreeUtil.getParentOfType(elementAtCaret, KtNamedFunction::class.java)
            ?: PsiTreeUtil.getParentOfType(elementAtCaret, PsiNamedElement::class.java)
    }
}






