/*
 Copyright 2010 LLC "Open Code"
 http://www.o-code.ru
 $HeadURL$
 $Author$
 $Revision$
 $Date:                    $
*/
package su.opencode.shuffler;

import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.*;

public class InliningVisitor extends JavaRecursiveElementWalkingVisitor {

	private void inlineElement(final PsiElement element){
		if (element == null) return;
		if (!(element instanceof PsiModifierListOwner)) return;

		boolean isPublic = ((PsiModifierListOwner) element).hasModifierProperty("public");

		if (isPublic) return;

		for (final InlineActionHandler handler : Extensions.getExtensions(InlineActionHandler.EP_NAME)) {
			if (handler.canInlineElement(element)) {
				ApplicationManager.getApplication().invokeAndWait(new Runnable() {
					@Override
					public void run() {
						ApplicationManager.getApplication().runWriteAction(new Runnable() {
							@Override
							public void run() {
								//TODO silently
								handler.inlineElement(element.getProject(), null, element);
							}
						});
					}
				}, ModalityState.defaultModalityState());
				return;
			}
		}
	}

	@Override
	public void visitMethod(final PsiMethod element) {
		super.visitMethod(element);
		inlineElement(element);
	}

	@Override
	public void visitClass(PsiClass element) {
		super.visitClass(element);
		inlineElement(element);
	}
}
