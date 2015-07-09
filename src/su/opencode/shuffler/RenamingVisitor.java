/*
 Copyright 2010 LLC "Open Code"
 http://www.o-code.ru
 $HeadURL$
 $Author$
 $Revision$
 $Date:                    $
*/
package su.opencode.shuffler;

import com.google.common.collect.Table;
import com.intellij.psi.*;
import com.intellij.refactoring.JavaRenameRefactoring;
import com.intellij.refactoring.SilentJavaRenameRefactoring;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class RenamingVisitor extends JavaRecursiveElementWalkingVisitor {

	private static int REFACTORING_ATTEMPTS = 5;
	private static final Set<String> JAVA_KEYWORDS;

	static {
		Set<String> keywords = new HashSet<String>();
		keywords.add("");
		keywords.add("default");
		for (Field f : PsiKeyword.class.getDeclaredFields()) {
			try {
				keywords.add((String)f.get(null));
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
		JAVA_KEYWORDS = Collections.unmodifiableSet(keywords);
	}


	private Table<String, String, Double> variableChains;
	private Table<String, String, Double> classChains;
	private Table<String, String, Double> methodChains;

	private boolean renamePrivate = true;
	private boolean renameProtected = true;
	private boolean renamePublic = false;
	private boolean renamePackage = false;

	public RenamingVisitor(Table<String, String, Double> variableChains,
						   Table<String, String, Double> classChains,
						   Table<String, String, Double> methodChains) {
		Validate.notNull(classChains);
		Validate.notNull(methodChains);
		Validate.notNull(variableChains);
		this.classChains = classChains;
		this.methodChains = methodChains;
		this.variableChains = variableChains;
	}

	public RenamingVisitor(MarkovBuildingVisitor markovBuilder) {
		this(markovBuilder.getVariableTable(), markovBuilder.getClassTable(), markovBuilder.getMethodTable());
	}

	protected boolean refactor(final PsiElement element,
							   final String newName,
							   final boolean checkNonJava) {

		final AtomicBoolean result = new AtomicBoolean(false);

		final JavaRenameRefactoring refactoring = new SilentJavaRenameRefactoring(element.getProject(),
																				  element, newName, checkNonJava);
		refactoring.run();

		return true;
	}

	private void processElement(PsiElement element) {
		if (element == null || !(element instanceof PsiModifierListOwner)) return;
		if (!element.isWritable()) return;
		if (!element.isPhysical()) return;
		if (element instanceof PsiTypeParameter) return;
		if (element instanceof PsiMethod &&
			(((PsiMethod)element).isConstructor() || !isMethodDeclaration((PsiMethod)element))) return;
		if (!(element instanceof PsiNamedElement) || ((PsiNamedElement)element).getName() == null) return;
		if (element.getOriginalElement() != element) return;

		boolean isPrivate = ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PRIVATE);
		boolean isProtected = ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PROTECTED);
		boolean isPublic = ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PUBLIC);
		boolean isPackage = ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
			&& !(element instanceof PsiLocalVariable);

		if (isPrivate && !renamePrivate
			|| isProtected && !renameProtected
			|| isPublic && !renamePublic
			|| isPackage && !renamePackage) {
			return;
		}

		int attempts = REFACTORING_ATTEMPTS;
		boolean refactored = false;

		String oldName = ((PsiNamedElement)element).getName();

		while (!refactored && attempts > 0) {
			String newName = null;
			do {
				attempts--;
				newName = generateName(element);
			} while (attempts > 0
					 && (JAVA_KEYWORDS.contains(newName.toLowerCase())
						 || StringUtils.isBlank(newName)
						 || oldName.equals(newName)));

			if (attempts > 0 && !StringUtils.isBlank(newName)) {
				refactored = refactor(element, newName, isPublic);
			}
		}
	}

	protected String generateName(PsiElement element) {
		Table<String, String, Double> chainTable = null;
		if (element instanceof PsiVariable){
			chainTable = variableChains;
		} else if (element instanceof PsiClass) {
			chainTable = classChains;
		} else if (element instanceof PsiMethod) {
			chainTable = methodChains;
		} else {
			throw new IllegalArgumentException();
		}

		List<String> name = generateNameList(chainTable);

		return conventionalizeName(element,name);
	}

	protected List<String> generateNameList(Table<String, String, Double> chainTable) {
		List<String> result = new ArrayList<String>();
		String current = "";
		while (result.isEmpty() || !"".equals(current)) {
			Map<String, Double> probs = chainTable.row(current);
			if (probs.isEmpty()) {
				current = "";
			} else {
				Iterator<Map.Entry<String, Double>> i = probs.entrySet().iterator();
				double rnd = ThreadLocalRandom.current().nextDouble();

				double sum = 0;
				Map.Entry<String, Double> e = null;
				while (i.hasNext() && rnd > sum) {
					e = i.next();
					sum += e.getValue();
				}
				current = e == null ? "" : e.getKey();
				result.add(current);
			}
		}
		return result;
	}

	protected String conventionalizeName(PsiElement element, List<String> name) {
		if (element instanceof PsiMethod) {
			return localName(name);
		} else if (element instanceof PsiClass) {
			return className(name);
		} else if (element instanceof PsiVariable) {
			PsiVariable var = (PsiVariable)element;
			if (var.hasModifierProperty("static") && var.hasModifierProperty("final")) {
				return constantName(name);
			} else {
				return localName(name);
			}
		}
		return null;
	}

	protected String className(List<String> name) {
		StringBuilder sb = new StringBuilder();
		for (String part : name) {
			if (StringUtils.isBlank(part)) continue;
			sb.append(StringUtils.capitalize(StringUtils.lowerCase(part)));
		}
		return sb.toString();
	}

	protected String localName(List<String> name) {
		StringBuilder sb = new StringBuilder();
		for (String part : name) {
			if (StringUtils.isBlank(part)) continue;
			sb.append(StringUtils.capitalize(StringUtils.lowerCase(part)));
		}
		char first = sb.charAt(0);
		first = Character.toLowerCase(first);
		sb.deleteCharAt(0);
		sb.insert(0, first);
		return sb.toString();
	}

	protected String constantName(List<String> name) {
		StringBuilder sb = new StringBuilder();
		for (String part : name) {
			if (StringUtils.isBlank(part)) continue;
			sb.append("_").append(StringUtils.upperCase(part));
		}
		sb.deleteCharAt(0);
		return sb.toString();
	}

	@Override
	public void visitVariable(PsiVariable element) {
		super.visitVariable(element);
		processElement(element);
	}

	@Override
	public void visitClass(PsiClass element) {
		super.visitClass(element);
		processElement(element);
	}

	@Override
	public void visitMethod(PsiMethod element) {
		super.visitMethod(element);
		processElement(element);
	}

	public boolean isRenamePrivate() {
		return renamePrivate;
	}

	public void setRenamePrivate(boolean renamePrivate) {
		this.renamePrivate = renamePrivate;
	}

	public boolean isRenameProtected() {
		return renameProtected;
	}

	public void setRenameProtected(boolean renameProtected) {
		this.renameProtected = renameProtected;
	}

	public boolean isRenamePublic() {
		return renamePublic;
	}

	public void setRenamePublic(boolean renamePublic) {
		this.renamePublic = renamePublic;
	}

	public boolean isRenamePackage() {
		return renamePackage;
	}

	public void setRenamePackage(boolean renamePackage) {
		this.renamePackage = renamePackage;
	}

	private static boolean isMethodDeclaration(PsiMethod element) {
		if (element == null) return false;
		PsiMethod[] sups = ShuffleAction.findDeepestSuperMethods(element);
		if (sups == null || sups.length == 0) return true;
		for (PsiMethod sup : sups) {
			if (sup.isEquivalentTo(element)) {
				return true;
			}
		}
		return false;
	}

}
