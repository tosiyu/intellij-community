// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class OverwrittenKeyInspection extends BaseJavaBatchLocalInspectionTool {
  private static final CallMatcher SET_ADD =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_SET, "add").parameterCount(1);
  private static final CallMatcher MAP_PUT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "put").parameterCount(2);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      Set<PsiMethodCallExpression> analyzed = new HashSet<>();

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiExpressionStatement statement = tryCast(call.getParent(), PsiExpressionStatement.class);
        if (statement == null) return;
        CallMatcher myMatcher;
        if (SET_ADD.test(call)) {
          myMatcher = SET_ADD;
        }
        else if (MAP_PUT.test(call)) {
          myMatcher = MAP_PUT;
        }
        else {
          return;
        }
        if (!analyzed.add(call)) return;

        Object key = getKey(call);
        if (key == null) return;
        PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getQualifierOrThis(call.getMethodExpression()));
        if (qualifier == null) return;
        PsiVariable qualifierVar =
          qualifier instanceof PsiReferenceExpression ? tryCast(((PsiReferenceExpression)qualifier).resolve(), PsiVariable.class) : null;
        Map<Object, List<PsiMethodCallExpression>> map = new HashMap<>();
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(call);
        while (true) {
          PsiExpressionStatement nextStatement =
            tryCast(PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class), PsiExpressionStatement.class);
          if (nextStatement == null) break;
          PsiMethodCallExpression nextCall = tryCast(nextStatement.getExpression(), PsiMethodCallExpression.class);
          if (!myMatcher.test(nextCall)) break;
          PsiExpression nextQualifier =
            PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getQualifierOrThis(nextCall.getMethodExpression()));
          if (nextQualifier == null || !PsiEquivalenceUtil.areElementsEquivalent(qualifier, nextQualifier)) break;
          analyzed.add(nextCall);
          if (qualifierVar != null && VariableAccessUtils.variableIsUsed(qualifierVar, nextCall.getArgumentList())) break;
          Object nextKey = getKey(nextCall);
          if (nextKey != null) {
            map.computeIfAbsent(nextKey, k -> new ArrayList<>()).add(nextCall);
          }
          statement = nextStatement;
        }
        for (List<PsiMethodCallExpression> calls : map.values()) {
          if (calls.size() < 2) continue;
          for (int i = 0; i < calls.size(); i++) {
            PsiMethodCallExpression dup = calls.get(i);
            PsiExpression arg = dup.getArgumentList().getExpressions()[0];
            LocalQuickFix fix = null;
            if (isOnTheFly) {
              PsiExpression nextArg = calls.get((i + 1) % calls.size()).getArgumentList().getExpressions()[0];
              fix = new NavigateToDuplicateFix(nextArg);
            }
            String message = myMatcher == SET_ADD ?
                              InspectionsBundle.message("inspection.overwritten.key.set.message") :
                              InspectionsBundle.message("inspection.overwritten.key.map.message");
            holder.registerProblem(arg, message, fix);
          }
        }
      }

      private Object getKey(PsiMethodCallExpression call) {
        PsiExpression key = call.getArgumentList().getExpressions()[0];
        Object constant = ExpressionUtils.computeConstantExpression(key);
        if (constant != null) {
          return constant;
        }
        if (key instanceof PsiReferenceExpression) {
          PsiField field = tryCast(((PsiReferenceExpression)key).resolve(), PsiField.class);
          if (field instanceof PsiEnumConstant ||
              field != null && field.hasModifierProperty(PsiModifier.FINAL) && field.hasModifierProperty(PsiModifier.STATIC)) {
            return field;
          }
        }
        return null;
      }
    };
  }

  private static class NavigateToDuplicateFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PsiExpression> myPointer;

    public NavigateToDuplicateFix(PsiExpression arg) {
      myPointer = SmartPointerManager.getInstance(arg.getProject()).createSmartPsiElementPointer(arg);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("navigate.to.duplicate.fix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiExpression element = myPointer.getElement();
      if (element == null) return;
      PsiFile file = element.getContainingFile();
      if (file == null) return;
      int offset = element.getTextRange().getStartOffset();
      new OpenFileDescriptor(project, file.getVirtualFile(), offset).navigate(true);
    }
  }
}
