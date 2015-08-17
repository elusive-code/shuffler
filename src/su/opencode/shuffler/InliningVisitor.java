/*
 	Shuffler is a plugin for IntelliJ Idea Community Edition,
 	that performs non-destructive java source code obfuscation.
    Copyright (C) 2015 LLC "Open Code" http://www.o-code.ru

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package su.opencode.shuffler;

import com.intellij.lang.refactoring.InlineActionHandler;
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
				handler.inlineElement(element.getProject(), null, element);
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
