/*
 Copyright 2010 LLC "Open Code"
 http://www.o-code.ru
 $HeadURL$
 $Author$
 $Revision$
 $Date:                    $
*/
package com.intellij.refactoring.rename;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.Processor;
import org.apache.commons.lang.StringUtils;
import su.opencode.shuffler.ShuffleAction;

import java.util.Map;

/*
Overrides for silently rejecting all changes that may break code.
*/
public class SilentRenameJavaVariableProcessor extends RenameJavaVariableProcessor {

	private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameProcessor");

	public static final SilentRenameJavaVariableProcessor INSTANCE = new SilentRenameJavaVariableProcessor();

	private boolean renameGettersAndSetters = true;

	public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
		if (element instanceof PsiField && JavaLanguage.INSTANCE.equals(element.getLanguage())) {
			prepareFieldRenaming((PsiField)element, newName, allRenames);
		}
	}

	private void prepareFieldRenaming(PsiField field, String newName, final Map<PsiElement, String> allRenames) {
		// search for getters/setters
		PsiClass aClass = field.getContainingClass();

		Project project = field.getProject();
		final JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);

		final String propertyName = manager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
		String newPropertyName = manager.variableNameToPropertyName(newName, VariableKind.FIELD);

		boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
		PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, propertyName, isStatic, false);
		PsiMethod setter = PropertyUtil.findPropertySetter(aClass, propertyName, isStatic, false);

		boolean shouldRenameSetterParameter = false;

		if (setter != null) {
			String parameterName = manager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
			PsiParameter setterParameter = setter.getParameterList().getParameters()[0];
			shouldRenameSetterParameter = parameterName.equals(setterParameter.getName());
		}

		String newGetterName = "";

		if (getter != null) {
			String getterId = getter.getName();
			newGetterName = PropertyUtil.suggestGetterName(newPropertyName, field.getType(), getterId);
			if (newGetterName.equals(getterId)) {
				getter = null;
				newGetterName = null;
			} else {
				for (PsiMethod method : ShuffleAction.findDeepestSuperMethods(getter)) {
					if (method instanceof PsiCompiledElement) {
						getter = null;
						break;
					}
				}
			}
		}

		String newSetterName = "";
		if (setter != null) {
			newSetterName = PropertyUtil.suggestSetterName(newPropertyName);
			final String newSetterParameterName = manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER);
			if (newSetterName.equals(setter.getName())) {
				setter = null;
				newSetterName = null;
				shouldRenameSetterParameter = false;
			}
			else if (newSetterParameterName.equals(setter.getParameterList().getParameters()[0].getName())) {
				shouldRenameSetterParameter = false;
			} else {
				for (PsiMethod method : ShuffleAction.findDeepestSuperMethods(setter)) {
					if (method instanceof PsiCompiledElement) {
						setter = null;
						shouldRenameSetterParameter = false;
						break;
					}
				}
			}
		}

		if ((getter != null || setter != null) && askToRenameAccesors(getter, setter, newName, project)) {
			getter = null;
			setter = null;
			shouldRenameSetterParameter = false;
		}

		if (getter != null) {
			addOverriddenAndImplemented(getter, newGetterName, allRenames);
		}

		if (setter != null) {
			addOverriddenAndImplemented(setter, newSetterName, allRenames);
		}

		if (shouldRenameSetterParameter) {
			PsiParameter parameter = setter.getParameterList().getParameters()[0];
			allRenames.put(parameter, manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER));
		}
	}

	private boolean askToRenameAccesors(PsiMethod getter, PsiMethod setter, String newName, final Project project) {
		if (getter != null && getter.hasModifierProperty("public")) return true;
		if (setter != null && setter.hasModifierProperty("public")) return true;

		PsiMethod[] superGetters = null;
		if (getter != null) {
			String newGetterName = "get"+ StringUtils.capitalize(newName);
			PsiClass clazz = getter.getContainingClass();
			PsiMethod[] collisions = clazz.findMethodsByName(newGetterName,true);
			for (PsiMethod method: collisions) {
				if (!ShuffleAction.isCollidingSignature(getter, method, true) && !getter.equals(method)) {
					return true;
				}
			}

			superGetters = ShuffleAction.findDeepestSuperMethods(getter);
		}

		PsiMethod[] superSetters = null;
		if (setter != null) {
			String newSetterName = "set"+ StringUtils.capitalize(newName);
			PsiClass clazz = setter.getContainingClass();
			PsiMethod[] collisions = clazz.findMethodsByName(newSetterName,true);
			for (PsiMethod method: collisions) {
				if (!ShuffleAction.isCollidingSignature(setter, method, true) && !setter.equals(method)) {
					return true;
				}
			}
			superSetters = ShuffleAction.findDeepestSuperMethods(setter);
		}

		return !(renameGettersAndSetters
				 && (superGetters == null || superGetters.length == 0)
				 && (superSetters == null || superSetters.length == 0));
	}

	private static void addOverriddenAndImplemented(PsiMethod methodPrototype, final String newName, final Map<PsiElement, String> allRenames) {
		allRenames.put(methodPrototype, newName);
		for (PsiMethod method : ShuffleAction.findDeepestSuperMethods(methodPrototype)) {
			OverridingMethodsSearch.search(method).forEach(new Processor<PsiMethod>() {
				public boolean process(PsiMethod psiMethod) {
					assertNonCompileElement(psiMethod);
					allRenames.put(psiMethod, newName);
					return true;
				}
			});
			allRenames.put(method, newName);
		}
	}

	protected static void assertNonCompileElement(PsiElement element) {
		LOG.assertTrue(!(element instanceof PsiCompiledElement));
	}


}
