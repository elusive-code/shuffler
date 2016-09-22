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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class DecommentingVisitor extends JavaRecursiveElementWalkingVisitor {

    private static final Logger LOG = Logger.getLogger(DecommentingVisitor.class.getName());

    private CommandProcessor processor   = CommandProcessor.getInstance();
    private Application      application = ApplicationManager.getApplication();
    private Set<PsiComment>  comments    = new HashSet<PsiComment>();

    @Override
    public void visitComment(PsiComment comment) {
        super.visitComment(comment);
        addComment(comment);
    }

    private void addComment(PsiComment comment) {
        if (comment == null) return;
        if (!comment.isPhysical() || !comment.isWritable()) return;
        if (comment instanceof PsiDocComment) return;
        if (comment instanceof TreeElement) {
            if (((TreeElement) comment).getTreeParent() == null) return;
        }

        PsiElement parent = comment.getParent();
        if (parent == null) return;

        if (parent instanceof PsiComment) {
            addComment((PsiComment) parent);
        } else {
            comments.add(comment);
        }
    }

    @Override
    public void visitFile(PsiFile file) {
        super.visitFile(file);
        processor.executeCommand(file.getProject(), new DecommentCommand(new Decommenter(comments)), "", "");
        file.subtreeChanged();
    }

    private class DecommentCommand implements Runnable {
        private Decommenter decommenter;

        public DecommentCommand(Decommenter decommenter) {
            this.decommenter = decommenter;
        }

        @Override
        public void run() {
            application.runWriteAction(decommenter);
        }
    }

    private static class Decommenter implements Runnable {
        private Set<PsiComment> comments;

        public Decommenter(Set<PsiComment> comments) {
            this.comments = comments;
        }

        @Override
        public void run() {
            for (PsiComment comment: comments){
                if (comment instanceof TreeElement) {
                    if (((TreeElement) comment).getTreeParent() == null) {
                        continue;
                    }
                }
                comment.delete();
            }
        }
    }
}
