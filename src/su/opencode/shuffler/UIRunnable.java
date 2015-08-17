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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

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
