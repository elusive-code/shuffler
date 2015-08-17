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
package com.intellij.refactoring.rename;

import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.impl.RefactoringTransaction;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.opencode.shuffler.ShuffleAction;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/*
Overrides for silently rejecting all changes that may break code.
*/
public class SilentRenameProcessor extends RenameProcessor {

	private PsiElement myPrimaryElement;
	private String myNewName;

	public SilentRenameProcessor(Project project, PsiElement element, @NotNull @NonNls String newName,
								 boolean isSearchInComments, boolean isSearchTextOccurrences) {
		super(project, element, newName, isSearchInComments, isSearchTextOccurrences);
		this.myPrimaryElement = element;
		this.myNewName = newName;
	}

	@Override
	public boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
		UsageInfo[] usagesIn = refUsages.get();
		MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

		RenameUtil.addConflictDescriptions(usagesIn, conflicts);
		RenamePsiElementProcessor.forElement(myPrimaryElement).findExistingNameConflicts(myPrimaryElement, myNewName, conflicts);
		if (!conflicts.isEmpty()) {
			return false;
		}

		for (UsageInfo usageInfo: usagesIn) {
			if (usageInfo instanceof CollisionUsageInfo) {
				return false;
			}
		}

		if (myPrimaryElement instanceof PsiVariable) {
			PsiMethod method = ShuffleAction.findRootPsiByType(myPrimaryElement, PsiMethod.class);
			if (method != null) {
				Set<PsiVariable> vars = ShuffleAction.findChildren(method, myNewName, PsiVariable.class);
				for (PsiVariable var : vars) {
					if (!myPrimaryElement.isEquivalentTo(var)) {
						return false;
					}
				}
			}
		}

		if (myPrimaryElement instanceof PsiMethod) {
			PsiClass psiClass = ShuffleAction.findParentPsiByType(myPrimaryElement, PsiClass.class);
			Set<PsiMethod> methods = ShuffleAction.findChildren(psiClass, myNewName, PsiMethod.class);
			for (PsiMethod method: methods){
				if (!myPrimaryElement.equals(method) && ShuffleAction.isCollidingSignature((PsiMethod)myPrimaryElement,
																						   method, true)) {
					return false;
				}
			}
		}

		prepareSuccessful();
		return canRename(myProject, null, myPrimaryElement);
	}

	@Override
	public boolean isPreviewUsages(UsageInfo[] usages) {
		return super.isPreviewUsages(usages);
	}

	@Override
	public void execute(UsageInfo[] usages) {
		super.execute(usages);
	}

	@NotNull
	@Override
	public Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
		return super.getElementsToWrite(descriptor);
	}

	@Override
	public RefactoringTransaction getTransaction() {
		return super.getTransaction();
	}

	@Override
	public UndoConfirmationPolicy getUndoConfirmationPolicy() {
		return super.getUndoConfirmationPolicy();
	}

	@Override
	public boolean isGlobalUndoAction() {
		return super.isGlobalUndoAction();
	}

	@Override
	public boolean isPreviewUsages() {
		return super.isPreviewUsages();
	}

	@Override
	public ConflictsDialog prepareConflictsDialog(MultiMap<PsiElement, String> conflicts,
													 @Nullable UsageInfo[] usages) {
		return super.prepareConflictsDialog(conflicts, usages);
	}

	@Override
	public void prepareSuccessful() {
		super.prepareSuccessful();
	}

	@Override
	public void previewRefactoring(UsageInfo[] usages) {
		super.previewRefactoring(usages);
	}

	@Override
	public boolean showConflicts(MultiMap<PsiElement, String> conflicts) {
		return super.showConflicts(conflicts);
	}

	@Override
	public boolean showConflicts(MultiMap<PsiElement, String> conflicts, @Nullable UsageInfo[] usages) {
		return super.showConflicts(conflicts, usages);
	}

	@Override
	public boolean showAutomaticRenamingDialog(AutomaticRenamer automaticVariableRenamer) {
		return super.showAutomaticRenamingDialog(automaticVariableRenamer);
	}

	@Override
	public void refreshElements(PsiElement[] elements) {
		super.refreshElements(elements);
	}

	@Override
	public void performPsiSpoilingRefactoring() {
		super.performPsiSpoilingRefactoring();
	}

	@Override
	public String getCommandName() {
		return super.getCommandName();
	}

	@NotNull
	@Override
	public UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
		return super.createUsageViewDescriptor(usages);
	}

	@Override
	public void prepareRenaming(@NotNull final PsiElement element, final String newName, final LinkedHashMap<PsiElement, String> allRenames) {
		final List<RenamePsiElementProcessor> processors = RenamePsiElementProcessor.allForElement(element);
		myForceShowPreview = false;
		for (RenamePsiElementProcessor processor : processors) {
			if (processor instanceof RenameJavaVariableProcessor){
				processor = SilentRenameJavaVariableProcessor.INSTANCE;
			}

			if (processor.canProcessElement(element)) {
				processor.prepareRenaming(element, newName, allRenames);
				myForceShowPreview |= processor.forcesShowPreview();
			}
		}
	}

	public static String renameabilityStatus(Project project, PsiElement element) {
		if (element == null) return "";

		boolean hasRenameProcessor = RenamePsiElementProcessor.forElement(element) != RenamePsiElementProcessor.DEFAULT;
		boolean hasWritableMetaData = element instanceof PsiMetaOwner && ((PsiMetaOwner)element).getMetaData() instanceof PsiWritableMetaData;

		if (!hasRenameProcessor && !hasWritableMetaData && !(element instanceof PsiNamedElement)) {
			return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.symbol.to.rename"));
		}

		if (!PsiManager.getInstance(project).isInProject(element)) {
			if (element.isPhysical()) {
				final String message = RefactoringBundle.message("error.out.of.project.element", UsageViewUtil.getType(element));
				return RefactoringBundle.getCannotRefactorMessage(message);
			}

			if (!element.isWritable()) {
				return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.cannot.be.renamed"));
			}
		}

		if (InjectedLanguageUtil.isInInjectedLanguagePrefixSuffix(element)) {
			final String message = RefactoringBundle.message("error.in.injected.lang.prefix.suffix", UsageViewUtil.getType(element));
			return RefactoringBundle.getCannotRefactorMessage(message);
		}

		return null;
	}

	public static boolean canRename(Project project, Editor editor, PsiElement element) throws CommonRefactoringUtil.RefactoringErrorHintException {
		String message = renameabilityStatus(project, element);
		if (message != null) {
			if (!message.isEmpty()) showErrorMessage(project, editor, message);

			return false;
		}
		return true;
	}

	public static void showErrorMessage(Project project, @Nullable Editor editor, String message) {
		CommonRefactoringUtil.showErrorHint(project, editor, message, RefactoringBundle.message("rename.title"), null);
	}


}
