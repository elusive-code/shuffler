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
import com.intellij.refactoring.JavaRenameRefactoring;
import com.intellij.refactoring.SilentJavaRenameRefactoring;

public class UIRunnable implements Runnable {

	private Runnable original;

	public UIRunnable(Runnable original) {
		this.original = original;
	}

	@Override
	public void run() {
		ApplicationManager.getApplication().invokeAndWait(new Runnable() {
			@Override
			public void run() {
				runInUI();
			}
		}, ModalityState.defaultModalityState());
	}

	private void runInUI(){
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			@Override
			public void run() {
				runWrite();
			}
		});
	}

	private void runWrite(){
		original.run();
	}

}
