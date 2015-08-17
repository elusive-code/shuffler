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
