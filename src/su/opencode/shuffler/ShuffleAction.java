/*
 Copyright 2010 LLC "Open Code"
 http://www.o-code.ru
 $HeadURL$
 $Author$
 $Revision$
 $Date:                    $
*/
package su.opencode.shuffler;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShuffleAction extends AnAction {

	private static final Logger LOG = Logger.getLogger(ShuffleAction.class.getName());

	private boolean includeLibraries = false;

	private boolean renamePublic = false;
	private boolean renamePackage = true;
	private boolean renameProtected = true;
	private boolean renamePrivate = true;

	private static final CacheLoader<PsiMethod, PsiMethod[]> ROOT_METHOD_LOADER = new CacheLoader<PsiMethod, PsiMethod[]>() {
		@Override
		public PsiMethod[] load(PsiMethod psiMethod) throws Exception {
			return psiMethod.findDeepestSuperMethods();
		}
	};

	private static final LoadingCache<PsiMethod, PsiMethod[]> ROOT_METHODS = CacheBuilder.newBuilder()
																						 .expireAfterAccess(2, TimeUnit.MINUTES)
																						 .build(ROOT_METHOD_LOADER);

	public static PsiMethod[] findDeepestSuperMethods(final PsiMethod method) {
		try {
			return ROOT_METHODS.get(method);
		} catch (ExecutionException ex) {
			LOG.log(Level.SEVERE, "Exception during root method cache loading", ex);
			return null;
		}
	}

	public static <T extends PsiElement> T findRootPsiByType(PsiElement element, Class<T> parentType) {
		if (element == null) return null;
		PsiElement lastFound = null;
		PsiElement current = element.getParent();
		while (current != null) {
			if (parentType.isAssignableFrom(current.getClass())) {
				lastFound = current;
			}
			current = current.getParent();
		}
		return (T)lastFound;
	}

	public static <T extends PsiElement> T findParentPsiByType(PsiElement element, Class<T> parentType) {
		if (element == null) return null;
		PsiElement current = element.getParent();
		while (current != null && !parentType.isAssignableFrom(current.getClass())) {
			current = current.getParent();
		}
		return (T)current;
	}

	public static <T extends PsiElement> Set<T> findParentPsisByType(PsiElement element, Class<T> parentType) {
		if (element == null) return Collections.<T>emptySet();
		LinkedHashSet<T> result = new LinkedHashSet<T>();
		PsiElement current = element.getParent();
		while (current != null) {
			if (parentType.isAssignableFrom(current.getClass())) {
				result.add((T)current);
			}
			current = current.getParent();
		}
		return result;
	}

	private static boolean isOfType(PsiElement element, Class type){
		return type == null
			   || element != null
				  && type.isAssignableFrom(element.getClass());
	}

	private static boolean isNamed(PsiElement element, String name){
		return name == null
			   || element != null
				  && element instanceof PsiNamedElement
				  && name.equals(((PsiNamedElement)element).getName());
	}

	public static <T extends PsiElement> Set<T> findChildren(PsiElement element, String name, Class<T> type) {
		LinkedHashSet results = new LinkedHashSet<T>();
		if (element == null) return results;

		Queue<PsiElement> elementsToCheck = new LinkedList<PsiElement>();
		elementsToCheck.add(element);

		while (elementsToCheck.size() > 0) {
			PsiElement e = elementsToCheck.poll();
			if (isOfType(e, type) && isNamed(e, name)) {
				results.add((T)e);
			}
			elementsToCheck.addAll(Arrays.asList(e.getChildren()));
		}

		return results;
	}


	public static boolean isCollidingSignature(PsiMethod method1, PsiMethod method2, boolean ignoreName) {
		if (!ignoreName && !method1.getName().equals(method2.getName())) return false;
		int paramCount = method1.getParameterList().getParametersCount();
		if (paramCount != method2.getParameterList().getParametersCount()) return false;

		PsiParameter[] params1 = method1.getParameterList().getParameters();
		PsiParameter[] params2 = method2.getParameterList().getParameters();

		for (int i = 0; i < paramCount; i++) {
			if (params1[i] == null || params2[i] == null) return false;
			PsiParameter param1 = params1[i];
			PsiParameter param2 = params2[i];

			if (param1.getType().equals(param2.getType())
				&& param1.getTypeElement()==null && param2.getTypeElement()==null) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void actionPerformed(AnActionEvent anActionEvent) {
		final Project project = anActionEvent.getProject();

		int exitCode = Messages.showOkCancelDialog(project,
			"Varaiable, class, and method names will be shuffled in current project, comments will be removed. \n" +
			"It will block Idea and may take awhile. \n" +
			"Do you want to shuffle?",
			"Shuffle project?",
			"Shuffle","Cancel",null);

		if (DialogWrapper.CANCEL_EXIT_CODE == exitCode) return;


		Task task = new Task.Modal(project,"Shuffling",false){

			@Override
			public void run(@NotNull ProgressIndicator indicator) {
				ShuffleRunner runner = new ShuffleRunner(indicator,project);
				runner.run();
			}
		};

		ProgressManager.getInstance().run(task);
	}

	public void processFile(final Project project,final VirtualFile file,final PsiElementVisitor... visitors){
		runInUI(new Runnable() {

			@Override
			public void run() {
				if (!file.exists()) return;
				PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

				if (!psiFile.isWritable() || !psiFile.isPhysical()) return;
				if (psiFile != psiFile.getOriginalElement()) return;

				for (PsiElementVisitor visitor: visitors){
					if (visitor == null) continue;
					psiFile.accept(visitor);
				}
			}

		});
	}

	private class ShuffleRunner implements Runnable {

		private Project project;
		private ProgressIndicator indicator;

		private ShuffleRunner(ProgressIndicator indicator, Project project) {
			this.indicator = indicator;
			this.project = project;
		}

		@Override
		public void run() {
			GlobalSearchScope scope;
			ROOT_METHODS.invalidateAll();
			indicator.setFraction(0);

			scope = GlobalSearchScopes.projectProductionScope(project);

			Collection<VirtualFile> projectFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE,scope);
			Collection<VirtualFile> markovChainSourceFiles;

			if (includeLibraries) {
				scope = new ProjectAndLibrariesScope(project);
				markovChainSourceFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE,scope);
			} else {
				markovChainSourceFiles = projectFiles;
			}
			indicator.setFraction(0.05);

			double total = markovChainSourceFiles.size();
			int counter = 0;

			MarkovBuildingVisitor chainBuilder = new MarkovBuildingVisitor();

			indicator.setText("Building Markov chain");
			LOG.info("Building Markov chain in project " + project.getName());
			for (VirtualFile file: markovChainSourceFiles){
				processFile(project, file, chainBuilder);
				indicator.setText2(file.getCanonicalPath());
				counter++;
				indicator.setFraction(0.05+0.1*counter/total);
			}
			LOG.info("Markov chain building finished, renaming in project " + project.getName());

			//shuffling
			DecommentingVisitor decommenter = new DecommentingVisitor();
			InliningVisitor inliner = null; //new InliningVisitor();

			RenamingVisitor renamer = new RenamingVisitor(chainBuilder);
			renamer.setRenamePrivate(renamePrivate);
			renamer.setRenameProtected(renameProtected);
			renamer.setRenamePublic(renamePublic);
			renamer.setRenamePackage(renamePackage);

			counter = 0;
			total = projectFiles.size();
			indicator.setText("Shuffling");

			for (VirtualFile file : projectFiles) {
				//LOG.info("Shuffling "+file.getName());
				indicator.setText2(file.getCanonicalPath());
				try {
					processFile(project, file, decommenter, inliner, renamer);
				} catch (Throwable ex) {
					LOG.log(Level.WARNING, "Failed to shuffle " + file.getName(),ex);
				}
				counter++;
				indicator.setFraction(0.15+0.85*counter/total);
			}

			ROOT_METHODS.invalidateAll();
			LOG.finer("Renaming finished " + project.getName());
		}
	}

	public static void runInUI(Runnable r){
		new UIRunnable(r).run();
	}
}
