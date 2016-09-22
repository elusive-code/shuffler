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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Table;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.refactoring.JavaRenameRefactoring;
import com.intellij.refactoring.SilentJavaRenameRefactoring;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ExecutionException;
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
	private boolean renamePackage = false;
	private boolean renameDefault = false;
    private boolean renamePublic = false;

	private Set<String> ignoreMarkerAnnotations = new HashSet<String>();

	private CacheLoader<String,Boolean> markerCacheLoader = new CacheLoader(){
		@Override
		public Object load(Object o) throws Exception {
			return isIgnoreMarker((String)o);
		}
	};
	private LoadingCache<String,Boolean> ignoreMarkerFlagsCache = CacheBuilder.newBuilder()
																		.build(markerCacheLoader);

	public RenamingVisitor(Table<String, String, Double> variableChains,
						   Table<String, String, Double> classChains,
						   Table<String, String, Double> methodChains) {
		Validate.notNull(classChains);
		Validate.notNull(methodChains);
		Validate.notNull(variableChains);
		this.classChains = classChains;
		this.methodChains = methodChains;
		this.variableChains = variableChains;

		Set<String> ignoreMarkers = new HashSet<String>();
		ignoreMarkers.add("javax.persistence.*");
		ignoreMarkers.add("javax.xml.bind.*");
		ignoreMarkers.add("org.hibernate.*");
		ignoreMarkers.add("org.codehaus.jackson.*");
		ignoreMarkers.add("com.fasterxml.jackson.*");
		ignoreMarkers.add("org.springframework.beans.factory.annotation.Autowired");
		this.ignoreMarkerAnnotations = Collections.unmodifiableSet(ignoreMarkers);
	}

	public RenamingVisitor(MarkovBuildingVisitor markovBuilder) {
		this(markovBuilder.getVariableTable(), markovBuilder.getClassTable(), markovBuilder.getMethodTable());
	}

	protected boolean refactor(final PsiElement element,
							   final String newName,
							   final boolean checkNonJava) {

		final JavaRenameRefactoring refactoring = new SilentJavaRenameRefactoring(element.getProject(),
																				  element, newName, checkNonJava);
		refactoring.run();

		return true;
	}

	private void processElement(PsiElement element) {
		if (element == null || !(element instanceof PsiModifierListOwner)) return;
		PsiModifierListOwner el = (PsiModifierListOwner) element;

		if (ignoreElement(el)) {
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
				refactored = refactor(element, newName, false);
			}
		}
	}

	protected boolean ignoreElement(PsiModifierListOwner element){
		if (element == null) return true;
		if (!element.isWritable()) return true;
		if (!element.isPhysical()) return true;
		if (element instanceof PsiTypeParameter) return true;
		if (element instanceof PsiMethod &&
			(((PsiMethod)element).isConstructor() || !isMethodDeclaration((PsiMethod)element))) return true;
		if (!(element instanceof PsiNamedElement) || ((PsiNamedElement)element).getName() == null) return true;
		if (element.getOriginalElement() != element) return true;
        		if (element instanceof PsiLocalVariable) return false;


		return isOverride(element)
			|| !renamePrivate && isPrivate(element)
			|| !renameProtected && isProtected(element)
			|| !renamePackage && isPackage(element)
			|| !renameDefault && isDefault(element)
			|| !renamePublic && isPublic(element)
			|| ignoreMarkerPresent(element)
			|| isSerializable(element);
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

	protected boolean isOverride(PsiModifierListOwner element){
		if (!(element instanceof PsiMethod)){
			return false;
		}
		PsiMethod method = (PsiMethod) element;
		List<HierarchicalMethodSignature> supers = method.getHierarchicalMethodSignature().getSuperSignatures();
		return supers != null && supers.size() > 0;
	}

	protected boolean isPublic(PsiModifierListOwner element) {
		if (element.hasModifierProperty(PsiModifier.PUBLIC)){
			return true;
		}
		if (element.hasModifierProperty(PsiModifier.PRIVATE)){
			return false;
		}
		if (element instanceof PsiMethod){
			Iterator<PsiMethod> i = OverridingMethodsSearch.search((PsiMethod)element).iterator();
			while (i.hasNext()){
				PsiMethod method = i.next();
				if (method.hasModifierProperty(PsiModifier.PUBLIC)){
					return true;
				}
			}
		}
		return false;
	}

	protected boolean isPrivate(PsiModifierListOwner element){
		return element.hasModifierProperty(PsiModifier.PRIVATE);
	}

	protected boolean isProtected(PsiModifierListOwner element){
		return element.hasModifierProperty(PsiModifier.PROTECTED);
	}

	protected boolean isPackage(PsiModifierListOwner element){
		return element.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && !(element instanceof PsiLocalVariable);
	}

    protected boolean isDefault(PsiModifierListOwner element){
        return element.hasModifierProperty(PsiModifier.DEFAULT) && !(element instanceof PsiLocalVariable);
    }

	protected boolean isSerializable(PsiModifierListOwner element) {
		if (element instanceof PsiField){
			PsiClass[] supers = ((PsiField) element).getContainingClass().getSupers();
			for (PsiClass sup: supers) {
				if (sup.getQualifiedName().equals(Serializable.class.getName())){
					return true;
				}
			}
		}
		return false;
	}

	protected boolean ignoreMarkerPresent(PsiModifierListOwner element) {
		if (element == null) return false;
		PsiModifierList modifierList = element.getModifierList();

		if (modifierList != null && modifierList.getChildren() != null) {
			for (PsiElement mod : modifierList.getChildren()) {
				if (!(mod instanceof PsiAnnotation)) continue;
				PsiAnnotation annotation = (PsiAnnotation)mod;
				try {
					if (ignoreMarkerFlagsCache.get(annotation.getQualifiedName())) {
						return true;
					}
				} catch (ExecutionException ex) {

					return true;
				}
			}
		}

		if (element instanceof PsiMethod){
			PsiMethod method = (PsiMethod) element;
			HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
			List<HierarchicalMethodSignature> signatureList = signature != null
															  ? signature.getSuperSignatures()
															  : (List)Collections.emptyList();
			for (HierarchicalMethodSignature parent: signatureList){
				if (ignoreMarkerPresent(parent.getMethod())){
					return true;
				}
			}
		}

		if (element instanceof PsiClass) {
			PsiClass clazz = (PsiClass) element;
			for (PsiClass c: clazz.getSupers()){
				if (ignoreMarkerPresent(c)){
					return true;
				}
			}
		}

		PsiElement parent = element.getParent();
		while (parent != null && !(parent instanceof PsiModifierListOwner)) {
			parent = parent.getParent();
			if (parent instanceof PsiFileSystemItem) {
				parent = null;
			}
		}
		return ignoreMarkerPresent((PsiModifierListOwner)parent);
	}

	protected boolean isIgnoreMarker(String name) {
		if (ignoreMarkerAnnotations.contains(name)) return true;
		String[] nameParts = name.split("\\.");
		StringBuilder sb = new StringBuilder();
		for (String part: nameParts){
			if (ignoreMarkerAnnotations.contains(sb.append(part).append(".").toString()+"*")) {
				return true;
			}
		}
		return false;
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

    public boolean isRenameDefault() {
        return renameDefault;
    }

    public void setRenameDefault(boolean renameDefault) {
        this.renameDefault = renameDefault;
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
