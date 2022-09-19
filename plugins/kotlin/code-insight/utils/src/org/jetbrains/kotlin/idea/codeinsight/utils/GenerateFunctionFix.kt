/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class GenerateFunctionFix(private val functionDefinitionText: String, private val bodyText: String) : LocalQuickFix {
    override fun getFamilyName() = name

    override fun startInWriteAction() = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(descriptor.psiElement)) return
        val klass = descriptor.psiElement.getNonStrictParentOfType<KtClass>() ?: return
        val factory = KtPsiFactory(klass)

        runWriteAction {
            val function = factory.createFunction(functionDefinitionText)
            if (bodyText.isNotEmpty()) function.bodyExpression?.replace(factory.createBlock(bodyText))
            klass.addDeclaration(function)
        }
    }
}