/*
 Copyright 2010 LLC "Open Code"
 http://www.o-code.ru
 $HeadURL$
 $Author$
 $Revision$
 $Date:                    $
*/
package su.opencode.shuffler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.javadoc.PsiDocComment;

public class DecommentingVisitor extends JavaRecursiveElementWalkingVisitor {

	@Override
	public void visitComment(final PsiComment comment) {
		super.visitComment(comment);
		if (!comment.isPhysical()||!comment.isWritable()) return;
		if (comment instanceof PsiDocComment) return;
		comment.delete();
	}
}
