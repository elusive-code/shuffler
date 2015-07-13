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
package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.openapi.impl.JavaRenameRefactoringImpl;
import com.intellij.refactoring.rename.SilentRenameProcessor;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;
import java.util.Set;

/*
 * Because {#myProcessor} is final and built-in processors contain ui-dialogs and staff
 * we have to add custom variable for processor and override all methods so that they use it instead of default one.
 */
public class SilentJavaRenameRefactoring extends JavaRenameRefactoringImpl {

	private PsiElement myPrimaryElement;
	private String myNewName;
	private SilentRenameProcessor silentProcessor;

	private SilentJavaRenameRefactoring(Project project, PsiElement element, String newName, boolean toSearchInComments,
									   boolean toSearchInNonJavaFiles) {

		super(project, element, newName, toSearchInComments, toSearchInNonJavaFiles);
		this.myPrimaryElement = element;
		this.myNewName = newName;
		this.silentProcessor = new SilentRenameProcessor(project,element,newName,toSearchInComments,toSearchInNonJavaFiles);
		this.setShouldRenameInheritors(true);
		this.setShouldRenameVariables(false);
		this.setInteractive(null);
		this.setPreviewUsages(false);
		this.setSearchInComments(toSearchInComments);
		this.setSearchInNonJavaFiles(toSearchInNonJavaFiles);
	}

	public SilentJavaRenameRefactoring(Project project, PsiElement element, String newName, boolean checkNonJava) {
		this(project, element, newName, false, checkNonJava);
	}

	public SilentJavaRenameRefactoring(Project project, PsiElement element, String newName) {
		this(project, element, newName,false,true);
	}

	@Override
	public UsageInfo[] findUsages() {
		return silentProcessor.findUsages();
	}

	@Override
	public void addElement(PsiElement element, String newName) {
		silentProcessor.addElement(element, newName);
	}

	@Override
	public Set<PsiElement> getElements() {
		return silentProcessor.getElements();
	}

	@Override
	public Collection<String> getNewNames() {
		return silentProcessor.getNewNames();
	}

	@Override
	public boolean isSearchInComments() {
		return silentProcessor.isSearchInComments();
	}

	@Override
	public boolean isSearchInNonJavaFiles() {
		return silentProcessor.isSearchTextOccurrences();
	}

	@Override
	public void setSearchInComments(boolean value) {
		super.setSearchInComments(value);
		silentProcessor.setSearchInComments(value);
	}

	@Override
	public void setSearchInNonJavaFiles(boolean value) {
		super.setSearchInNonJavaFiles(value);
		silentProcessor.setSearchTextOccurrences(value);
	}

	@Override
	public void doRefactoring(UsageInfo[] usages) {
		silentProcessor.execute(usages);
	}

	@Override
	public boolean isInteractive() {
		return silentProcessor.myPrepareSuccessfulSwingThreadCallback != null;
	}

	@Override
	public boolean isPreviewUsages() {
		return silentProcessor.isPreviewUsages();
	}

	@Override
	public boolean preprocessUsages(Ref<UsageInfo[]> usages) {
		return silentProcessor.preprocessUsages(usages);
	}

	@Override
	public void run() {
		silentProcessor.run();
	}

	@Override
	public void setInteractive(Runnable prepareSuccessfulCallback) {
		super.setInteractive(prepareSuccessfulCallback);
		silentProcessor.setPrepareSuccessfulSwingThreadCallback(prepareSuccessfulCallback);
	}

	@Override
	public void setPreviewUsages(boolean value) {
		super.setPreviewUsages(value);
		silentProcessor.setPreviewUsages(value);
	}

	@Override
	public boolean shouldPreviewUsages(UsageInfo[] usages) {
		//return super.shouldPreviewUsages(usages);
		return silentProcessor.isPreviewUsages(usages);
	}
}
