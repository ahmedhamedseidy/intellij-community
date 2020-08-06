/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.editor.fixers.endLine
import org.jetbrains.kotlin.idea.editor.fixers.startLine
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.psi.stubs.elements.KtFunctionElementType

class KotlinFoldingBuilder : CustomFoldingBuilder(), DumbAware {

    private val collectionFactoryFunctionsNames: Set<String> =
        setOf(
            "arrayOf", "booleanArrayOf", "byteArrayOf", "charArrayOf", "doubleArrayOf",
            "floatArrayOf", "intArrayOf", "longArrayOf", "shortArrayOf", "arrayListOf",
            "hashMapOf", "hashSetOf",
            "linkedMapOf", "linkedSetOf", "linkedStringMapOf", "linkedStringSetOf",
            "listOf", "listOfNotNull",
            "mapOf",
            "mutableListOf", "mutableMapOf", "mutableSetOf",
            "setOf",
            "sortedMapOf", "sortedSetOf",
            "stringMapOf", "stringSetOf"
        )

    override fun buildLanguageFoldRegions(
        descriptors: MutableList<FoldingDescriptor>,
        root: PsiElement, document: Document, quick: Boolean
    ) {
        if (root !is KtFile) {
            return
        }

        val importList = root.importList
        if (importList != null) {
            val firstImport = importList.imports.firstOrNull()
            if (firstImport != null && importList.imports.size > 1) {
                val importKeyword = firstImport.firstChild

                val startOffset = importKeyword.endOffset + 1
                val endOffset = importList.endOffset

                descriptors.add(FoldingDescriptor(importList, TextRange(startOffset, endOffset)).apply {
                    setCanBeRemovedWhenCollapsed(true)
                })
            }
        }

        appendDescriptors(root.node, document, descriptors)
    }

    private fun appendDescriptors(node: ASTNode, document: Document, descriptors: MutableList<FoldingDescriptor>) {
        if (needFolding(node, document)) {
            val textRange = getRangeToFold(node, document)
            val relativeRange = textRange.shiftRight(-node.textRange.startOffset)
            val foldRegionText = node.chars.subSequence(relativeRange.startOffset, relativeRange.endOffset)
            if (StringUtil.countNewLines(foldRegionText) > 0) {
                descriptors.add(FoldingDescriptor(node, textRange))
            }
        }

        var child = node.firstChildNode
        while (child != null) {
            appendDescriptors(child, document, descriptors)
            child = child.treeNext
        }
    }

    private fun needFolding(node: ASTNode, document: Document): Boolean {
        val type = node.elementType
        val parentType = node.treeParent?.elementType

        if (type is KtFunctionElementType) {
            val bodyExpression = (node.psi as? KtNamedFunction)?.bodyExpression
            if (bodyExpression != null && bodyExpression !is KtBlockExpression) return true
        }

        return type == KtNodeTypes.FUNCTION_LITERAL ||
                (type == KtNodeTypes.BLOCK && parentType != KtNodeTypes.FUNCTION_LITERAL) ||
                type == KtNodeTypes.CLASS_BODY || type == KtTokens.BLOCK_COMMENT || type == KDocTokens.KDOC ||
                type == KtNodeTypes.STRING_TEMPLATE || type == KtNodeTypes.PRIMARY_CONSTRUCTOR || type == KtNodeTypes.WHEN ||
                node.shouldFoldCollection(document)
    }

    private fun ASTNode.shouldFoldCollection(document: Document): Boolean {
        val call = psi as? KtCallExpression ?: return false
        if (DumbService.isDumb(call.project)) return false

        if (call.valueArguments.size < 2) return false

        // Similar check will be done latter, but we still use it here to avoid unnecessary resolve.
        if (call.startLine(document) == call.endLine(document)) return false

        val reference = call.referenceExpression() ?: return false
        return !reference.mainReference.resolvesByNames.any {
                name -> name.isSpecial || name.identifier !in collectionFactoryFunctionsNames
        }
    }

    private fun getRangeToFold(node: ASTNode, document: Document): TextRange {
        if (node.elementType is KtFunctionElementType) {
            val function = node.psi as? KtNamedFunction
            val bodyExpression = function?.bodyExpression
            if (bodyExpression != null && bodyExpression !is KtBlockExpression) {
                if (function.startLine(document) != bodyExpression.startLine(document)) {
                    val lineBreak = bodyExpression.siblings(forward = false, withItself = false).firstOrNull { "\n" in it.text }
                    if (lineBreak != null) {
                        return TextRange(lineBreak.startOffset, bodyExpression.endOffset)
                    }
                }
                return bodyExpression.textRange
            }
        }

        if (node.elementType == KtNodeTypes.FUNCTION_LITERAL) {
            val psi = node.psi as? KtFunctionLiteral
            val lbrace = psi?.lBrace
            val rbrace = psi?.rBrace
            if (lbrace != null && rbrace != null) {
                return TextRange(lbrace.startOffset, rbrace.endOffset)
            }
        }

        if (node.elementType == KtNodeTypes.CALL_EXPRESSION) {
            val valueArgumentList = (node.psi as? KtCallExpression)?.valueArgumentList
            val leftParenthesis = valueArgumentList?.leftParenthesis
            val rightParenthesis = valueArgumentList?.rightParenthesis
            if (leftParenthesis != null && rightParenthesis != null) {
                return TextRange(leftParenthesis.startOffset, rightParenthesis.endOffset)
            }
        }

        if (node.elementType == KtNodeTypes.WHEN) {
            val whenExpression = node.psi as? KtWhenExpression
            val openBrace = whenExpression?.openBrace
            val closeBrace = whenExpression?.closeBrace
            if (openBrace != null && closeBrace != null) {
                return TextRange(openBrace.startOffset, closeBrace.endOffset)
            }
        }

        return node.textRange
    }

    override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String = when {
        node.elementType == KtTokens.BLOCK_COMMENT -> "/${getFirstLineOfComment(node)}.../"
        node.elementType == KDocTokens.KDOC -> "/**${getFirstLineOfComment(node)}...*/"
        node.elementType == KtNodeTypes.STRING_TEMPLATE -> "\"\"\"${getTrimmedFirstLineOfString(node).addSpaceIfNeeded()}...\"\"\""
        node.elementType == KtNodeTypes.PRIMARY_CONSTRUCTOR || node.elementType == KtNodeTypes.CALL_EXPRESSION -> "(...)"
        node.psi is KtImportList -> "..."
        else -> "{...}"
    }

    private fun getTrimmedFirstLineOfString(node: ASTNode): String {
        val lines = node.text.split("\n")
        val firstLine = lines.asSequence().map { it.replace("\"\"\"", "").trim() }.firstOrNull(String::isNotEmpty)
        return firstLine ?: ""
    }

    private fun String.addSpaceIfNeeded(): String {
        if (isEmpty() || endsWith(" ")) return this
        return "$this "
    }

    private fun getFirstLineOfComment(node: ASTNode): String {
        val targetCommentLine = node.text.split("\n").firstOrNull {
            getCommentContents(it).isNotEmpty()
        } ?: return ""
        return " ${getCommentContents(targetCommentLine)} "
    }

    private fun getCommentContents(line: String): String {
        return line.trim()
            .removePrefix("/**")
            .removePrefix("/*")
            .removePrefix("*/")
            .removePrefix("*")
            .trim()
    }

    override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
        val settings = JavaCodeFoldingSettings.getInstance()

        if (node.psi is KtImportList) {
            return settings.isCollapseImports
        }

        val type = node.elementType
        if (type == KtTokens.BLOCK_COMMENT || type == KDocTokens.KDOC) {
            if (isFirstElementInFile(node.psi)) {
                return settings.isCollapseFileHeader
            }
        }

        return false
    }

    override fun isCustomFoldingRoot(node: ASTNode) = node.elementType == KtNodeTypes.BLOCK || node.elementType == KtNodeTypes.CLASS_BODY

    private fun isFirstElementInFile(element: PsiElement): Boolean {
        val parent = element.parent
        if (parent is KtFile) {
            val firstNonWhiteSpace = parent.allChildren.firstOrNull {
                it.textLength != 0 && it !is PsiWhiteSpace
            }

            return element == firstNonWhiteSpace
        }

        return false
    }
}
