/*
 Copyright 2010 LLC "Open Code"
 http://www.o-code.ru
 $HeadURL$
 $Author$
 $Revision$
 $Date:                    $
*/
package su.opencode.shuffler;

import com.google.common.base.Strings;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.intellij.psi.*;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

public class MarkovBuildingVisitor extends JavaRecursiveElementWalkingVisitor {

	private static final double MODIFIER = 100;

	private static final Pattern NON_ALPHA_PATTERN = Pattern.compile("[\\W_]");

	private Table<String,String,Integer> variableTable = HashBasedTable.create();
	private Table<String,String,Integer> classTable = HashBasedTable.create();
	private Table<String,String,Integer> methodTable = HashBasedTable.create();

	private void processName(String s, Table<String, String, Integer> chainTable) {
		if (StringUtils.isBlank(s)) return;
		String[] parts = StringUtils.splitByCharacterTypeCamelCase(s);

		String from = "";
		String to = "";

		for (int i = 0; i <= parts.length; i++) {
			to = (i == parts.length ? "" : parts[i].toLowerCase());
			if (NON_ALPHA_PATTERN.matcher(to).find()) {
				continue;
			}
			synchronized (chainTable) {
				Integer counter = chainTable.get(from, to);
				if (counter == null) {
					counter = 1;
				} else {
					counter++;
				}
				chainTable.put(from, to, counter);
			}
			from = to;
		}
	}

	@Override
	public void visitVariable(PsiVariable variable) {
		super.visitVariable(variable);
		processName(variable.getName(),variableTable);
	}

	@Override
	public void visitClass(PsiClass aClass) {
		super.visitClass(aClass);
		processName(aClass.getName(),classTable);
	}

	@Override
	public void visitMethod(PsiMethod method) {
		super.visitMethod(method);
		processName(method.getName(),methodTable);
	}

	private Table<String, String, Double> probabilityTable(Table<String, String, Integer> chainTable) {

		ImmutableTable.Builder<String, String, Double> builder = ImmutableTable.builder();

		for (Map.Entry<String, Map<String, Integer>> row : chainTable.rowMap().entrySet()) {
			if (row.getValue().isEmpty()) continue;

			double total = 0;

			for (Map.Entry<String, Integer> cell : row.getValue().entrySet()) {
				total += cell.getValue();
			}

			for (Map.Entry<String, Integer> cell : row.getValue().entrySet()) {
				double prob = cell.getValue() / total;
				builder.put(row.getKey(), cell.getKey(), prob);
			}
		}

		builder.orderRowsBy(StringComparator.INSTANCE);
		builder.orderColumnsBy(StringComparator.INSTANCE);

		return builder.build();
	}

	public Table<String, String, Double> getClassTable() {
		return probabilityTable(classTable);
	}

	public Table<String, String, Double> getMethodTable() {
		return probabilityTable(methodTable);
	}

	public Table<String, String, Double> getVariableTable() {
		return probabilityTable(variableTable);
	}

	private static class StringComparator implements Comparator<String>{
		public static final StringComparator INSTANCE = new StringComparator();

		private StringComparator() {
		}

		@Override
		public int compare(String o1, String o2) {
			if (o1 == null && o2 == null) return 0;
			if (o1 == null) return -1;
			if (o2 == null) return 1;
			return o1.compareTo(o2);
		}
	}
}
