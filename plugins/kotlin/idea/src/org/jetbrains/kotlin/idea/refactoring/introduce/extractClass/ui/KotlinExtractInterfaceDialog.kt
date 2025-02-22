// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ui

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.classMembers.MemberInfoModel
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ExtractSuperInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractInterfaceHandler
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.extractClassMembers
import org.jetbrains.kotlin.idea.refactoring.memberInfo.lightElementForMemberInfo
import org.jetbrains.kotlin.idea.refactoring.pullUp.getInterfaceContainmentVerifier
import org.jetbrains.kotlin.idea.refactoring.pullUp.isAbstractInInterface
import org.jetbrains.kotlin.idea.refactoring.pullUp.mustBeAbstractInInterface
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class KotlinExtractInterfaceDialog(
    originalClass: KtClassOrObject,
    targetParent: PsiElement,
    conflictChecker: (KotlinExtractSuperDialogBase) -> Boolean,
    refactoring: (ExtractSuperInfo) -> Unit
) : KotlinExtractSuperDialogBase(
    originalClass,
    targetParent,
    conflictChecker,
    true,
    KotlinExtractInterfaceHandler.REFACTORING_NAME,
    refactoring
) {
    companion object {
        private const val DESTINATION_PACKAGE_RECENT_KEY = "KotlinExtractInterfaceDialog.RECENT_KEYS"
    }

    init {
        init()
    }

    override fun createMemberInfoModel(): MemberInfoModelBase {
        val extractableMemberInfos = extractClassMembers(originalClass).filterNot {
            val member = it.member
            member is KtClass && member.hasModifier(KtTokens.INNER_KEYWORD) ||
                    member is KtParameter && member.hasModifier(KtTokens.PRIVATE_KEYWORD)
        }
        extractableMemberInfos.forEach { it.isToAbstract = true }
        return object : MemberInfoModelBase(
            originalClass,
            extractableMemberInfos,
            getInterfaceContainmentVerifier { selectedMembers }
        ) {
            override fun isMemberEnabled(member: KotlinMemberInfo): Boolean {
                if (!super.isMemberEnabled(member)) return false

                val declaration = member.member
                return !(declaration.hasModifier(KtTokens.INLINE_KEYWORD) ||
                        declaration.hasModifier(KtTokens.EXTERNAL_KEYWORD) ||
                        declaration.hasModifier(KtTokens.LATEINIT_KEYWORD))
            }

            override fun isAbstractEnabled(memberInfo: KotlinMemberInfo): Boolean {
                if (!super.isAbstractEnabled(memberInfo)) return false
                val member = memberInfo.member
                if (member.isAbstractInInterface(originalClass)) return false
                if (member.isConstructorDeclaredProperty()) return false
                return member is KtNamedFunction || (member is KtProperty && !member.mustBeAbstractInInterface()) || member is KtParameter
            }

            override fun isAbstractWhenDisabled(memberInfo: KotlinMemberInfo): Boolean {
                val member = memberInfo.member
                return member is KtProperty || member.isAbstractInInterface(originalClass) || member.isConstructorDeclaredProperty()
            }

            override fun checkForProblems(memberInfo: KotlinMemberInfo): Int {
                val result = super.checkForProblems(memberInfo)
                if (result != MemberInfoModel.OK) return result

                if (!memberInfo.isSuperClass || memberInfo.overrides != false || memberInfo.isChecked) return result

                val psiSuperInterface = lightElementForMemberInfo(memberInfo.member) as? PsiClass ?: return result

                for (info in memberInfos) {
                    if (!info.isChecked || info.isToAbstract) continue
                    val member = info.member ?: continue
                    val psiMethodToCheck = lightElementForMemberInfo(member) as? PsiMethod ?: continue
                    if (psiSuperInterface.findMethodBySignature(psiMethodToCheck, true) != null) return MemberInfoModel.ERROR
                }

                return result
            }
        }
    }

    override fun getDestinationPackageRecentKey() = DESTINATION_PACKAGE_RECENT_KEY

    override fun getClassNameLabelText() = RefactoringBundle.message("interface.name.prompt")!!

    override fun getPackageNameLabelText() = RefactoringBundle.message("package.for.new.interface")!!

    override fun getEntityName() = RefactoringBundle.message("extractSuperInterface.interface")!!

    override fun getTopLabelText() = RefactoringBundle.message("extract.interface.from")!!

    override fun getDocCommentPolicySetting() = KotlinRefactoringSettings.instance.EXTRACT_INTERFACE_JAVADOC

    override fun setDocCommentPolicySetting(policy: Int) {
        KotlinRefactoringSettings.instance.EXTRACT_INTERFACE_JAVADOC = policy
    }

    override fun getExtractedSuperNameNotSpecifiedMessage() = RefactoringBundle.message("no.interface.name.specified")!!

    override fun getHelpId() = HelpID.EXTRACT_INTERFACE

    override fun createExtractedSuperNameField() = super.createExtractedSuperNameField().apply { text = "I${originalClass.name}" }
}