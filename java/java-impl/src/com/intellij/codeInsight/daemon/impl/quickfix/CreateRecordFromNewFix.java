// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CreateRecordFromNewFix extends CreateClassFromNewFix {
  public CreateRecordFromNewFix(PsiNewExpression newExpression) {
    super(newExpression);
  }

  @NotNull
  @Override
  protected CreateClassKind getKind() {
    return CreateClassKind.RECORD;
  }

  @NotNull
  @Override
  TemplateBuilderImpl createConstructorTemplate(PsiClass aClass, PsiNewExpression newExpression, PsiExpressionList argList) {
    TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(aClass);
    PsiRecordHeader header = aClass.getRecordHeader();
    setupRecordComponents(header, templateBuilder, argList, getTargetSubstitutor(newExpression));
    return templateBuilder;
  }

  /**
   * @param header      header to set up record components
   * @param builder     builder to use
   * @param list        <ul>
   *                    <li>{@code PsiExpressionList} if the record is created from a new expression</li>
   *                    <li>{@code PsiDeconstructionList} if the record is created from a record pattern</li>
   *                    </ul>
   * @param substitutor substitutor to use
   * @throws IncorrectOperationException if setting up of record components fails
   */
  static void setupRecordComponents(@Nullable PsiRecordHeader header, @NotNull TemplateBuilder builder,
                                    @NotNull PsiElement list, @NotNull PsiSubstitutor substitutor)
    throws IncorrectOperationException {
    if (header == null || !(list instanceof PsiExpressionList || list instanceof PsiDeconstructionList)) return;
    PsiCaseLabelElement[] elements = list instanceof PsiExpressionList argumentList
                                     ? argumentList.getExpressions()
                                     : ((PsiDeconstructionList)list).getDeconstructionComponents();
    final PsiManager psiManager = header.getManager();
    final Project project = psiManager.getProject();

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    GlobalSearchScope resolveScope = header.getResolveScope();
    GuessTypeParameters guesser = new GuessTypeParameters(project, JavaPsiFacade.getElementFactory(project), builder, substitutor);

    final PsiClass containingClass = header.getContainingClass();
    if (containingClass == null) return;
    class ComponentData {
      final PsiType myType;
      final String[] myNames;

      ComponentData(PsiType type, String[] names) {
        myType = type;
        myNames = names;
      }

      @Override
      public String toString() {
        return myType.getCanonicalText() + " " + myNames[0];
      }
    }
    List<ComponentData> components = new ArrayList<>();
    //255 is the maximum number of record components
    for (int i = 0; i < Math.min(elements.length, 255); i++) {
      PsiCaseLabelElement element = elements[i];
      PsiType type = element instanceof PsiExpression expression
                     ? CommonJavaRefactoringUtil.getTypeByExpression(expression)
                     : JavaPsiPatternUtil.getPatternType(element);
      @NonNls String[] names = suggestVariableName(project, element, type).names;

      if (names.length == 0) {
        names = new String[]{"c" + i};
      }

      type = CreateFromUsageUtils.getParameterTypeByArgumentType(type, psiManager, resolveScope);
      components.add(new ComponentData(type, names));
    }
    PsiRecordHeader newHeader = factory.createRecordHeaderFromText(StringUtil.join(components, ", "), containingClass);
    PsiRecordHeader replacedHeader = (PsiRecordHeader)header.replace(newHeader);
    PsiRecordComponent[] recordComponents = replacedHeader.getRecordComponents();
    assert recordComponents.length == components.size();
    for (int i = 0; i < recordComponents.length; i++) {
      PsiRecordComponent component = recordComponents[i];
      ComponentData data = components.get(i);

      ExpectedTypeInfo info = ExpectedTypesProvider.createInfo(data.myType, ExpectedTypeInfo.TYPE_OR_SUPERTYPE, data.myType, TailType.NONE);

      PsiElement context = PsiTreeUtil.getParentOfType(list, PsiClass.class, PsiMethod.class);
      guesser.setupTypeElement(Objects.requireNonNull(component.getTypeElement()), new ExpectedTypeInfo[]{info}, context, containingClass);

      Expression expression = new CreateFromUsageUtils.ParameterNameExpression(data.myNames);
      builder.replaceElement(Objects.requireNonNull(component.getNameIdentifier()), expression);
    }
  }

  private static SuggestedNameInfo suggestVariableName(@NotNull Project project,
                                                       @NotNull PsiCaseLabelElement element,
                                                       @Nullable PsiType type) {
    if (element instanceof PsiExpression expression) {
      return JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.PARAMETER, null, expression, type);
    }
    PsiPatternVariable variable = JavaPsiPatternUtil.getPatternVariable(element);
    List<String> semanticNames = variable != null ? Collections.singletonList(variable.getName()) : Collections.emptyList();
    return JavaCodeStyleManager.getInstance(project).suggestNames(semanticNames, VariableKind.PARAMETER, type);
  }
}
