import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class Commenter {

	public static String configText;
	public static String prefixString;
	public static HashMap<String, String> lookup = new HashMap<>();

	public static boolean findNextAlpha(int ind, String str) {
		str = str.toLowerCase();
		for(; ind < str.length(); ++ind) {
			char c = str.charAt(ind);
			if("aeoiu".contains(Character.toString(c))) {
				return true;
			} else if('a' <= c && c <= 'z'){
				return false;
			}
		}
		return false;
	}

	public static String insertA(String line) {
		boolean isPerc = false;
		StringBuilder output = new StringBuilder();
		for(int i = 0; i < line.length(); ++i) {
			char c = line.charAt(i);
			if(isPerc) {
				if(c == 'a') {
					output.append(findNextAlpha(i + 1, line) ? "an" : "a");
				} else if(c == 'A') {
					output.append(findNextAlpha(i + 1, line) ? "An" : "A");
				}
				isPerc = false;
				continue;
			} else if(c == '%') {
				isPerc = true;
				continue;
			}
			output.append(c);
		}
		return output.toString();
	}

	public static String stringModifiers(EnumSet<Modifier> modifiers) {
		Iterator<Modifier> it = modifiers.iterator();
		ArrayList<Replace> replace = new ArrayList<>();
		String format = lookup.get("modifiers.format");
		String allowed = lookup.get("modifiers.allowed");
		while(it.hasNext()) {
			Modifier li = it.next();
			if(!allowed.contains(li.asString())) {
				continue;
			}
			HashMap<String, String> str = new HashMap<>();
			String repl = lookup.get("modifiers.remap<" + li.asString() + ">");
			if(repl == null) {
				repl = li.asString();
			}
			str.put("m", repl);
			replace.add(new Replace(str, 0));
		}
		return convertFormat(format, "m", replace);
	}

	public static String stringType(Type type) {
		int dimension = 0;
		while(type.isArrayType()) {
			type = type.asArrayType().getComponentType();
			++dimension;
		}
		String str = type.asString();

		if(dimension == 0) {
			return convertFormat(lookup.get("type"), "t", str);
		} else if(dimension == 1) {
			return convertFormat(lookup.get("type.array"), "t", str);
		} else {
			return convertFormat(lookup.get("type.mdarray"), "n", Integer.toString(dimension), "t", str);
		}
	}

	public static void loadConfig(File configFile) throws IOException {
		configText = new String(Files.readAllBytes(configFile.toPath()), "UTF-8");
		for(String line : configText.split("\n+")) {
			if(!line.trim().startsWith("#")) {
				String[] vals = line.split("=", 2);
				if(vals.length != 2) {
					System.err.println("Config file error: No '=' found. ");
					System.err.println(line);
				}
				lookup.put(vals[0].trim(), vals[1].trim());
			}
		}
		StringBuilder bdr = new StringBuilder();
		int cnt = Integer.parseInt(lookup.get("prefix_space"));
		while(cnt-- > 0) {
			bdr.append(" ");
		}
		prefixString = bdr.toString();
	}

	public static String convertFormat(String format, Object... obj) {
		format = format
				.replace("\\[", "#**BRACKET_OPEN**#")
				.replace("\\]", "#**BRACKET_CLOSE**#")
				.replace("\\(", "#**PAREN_OPEN**#")
				.replace("\\)", "#**PAREN_CLOSE**#")
				.replace("\\|", "#**PIPE**#")
				.replace("\\%", "#**PERC**#")
				.replace("\\$", "#**DOLLAR**#")
				.replace("\\s", " ")
				.replace("\\\\", "\\");
		HashMap<String, Object> replacement = new HashMap<>();
		String str = null;
		for(int i = 0; i < obj.length; ++i) {
			if(i % 2 == 0) {
				str = (String) obj[i];
			} else {
				replacement.put(str, obj[i]);
			}
		}
		StringBuilder builder = new StringBuilder();
		boolean isVar = false;
		for(int i = 0; i < format.length(); ++i) {
			char c = format.charAt(i);
			if(isVar) {
				if(c == '(') {
					int ind = format.indexOf(')', i);
					if(ind == -1) {
						System.err.println("')' not found");
						continue;
					}
					String arr = format.substring(i + 2, ind - 1);
					char chr = format.charAt(ind + 1);
					Object oo = replacement.get(Character.toString(chr));
					if(!(oo instanceof ArrayList)) {
						System.err.println("No list replacement found, ignoring");
						continue;
					}
					ArrayList<Replace> repl = (ArrayList<Replace>)oo;
					String[] splitted = arr.split("]\\[");
					for(int k = 0; k < repl.size(); ++k) {
						int ii = k == 0 ? 0 : (k == repl.size() - 1 ? 2 : 1);
						Replace rr = repl.get(k);
						String s = splitted[ii].split("\\|")[rr.ind];
						for(Map.Entry<String, String> rep : rr.repl.entrySet()) {
							s = s.replace("$" + rep.getKey(), rep.getValue());
						}
						builder.append(s);
					}
					i = ind + 1;
				} else if('a' <= c && c <= 'z') {
					builder.append(replacement.get(Character.toString(c)));
				}
				isVar = false;
				continue;
			}

			switch(c) {
				case '$':
					isVar = true;
					continue;
			}

			builder.append(c);
		}
		return insertA(builder.toString()
				.replace("#**BRACKET_OPEN**#", "[")
				.replace("#**BRACKET_CLOSE**#", "]")
				.replace("#**PAREN_OPEN**#", "(")
				.replace("#**PAREN_CLOSE**#", ")")
				.replace("#**PIPE**#", "|")
				.replace("#**DOLLAR**#", "$"))
				.replace("#**PERC**#", "%");
	}

	public static Comment newComment(String key, Object... vals) {
		return new LineComment(prefixString + convertFormat(lookup.get(key), vals));
	}

	public static void main(String[] args) throws Exception {
		loadConfig(new File("config"));

//		ArrayList<Replace> replacements = new ArrayList<Replace>();
//		HashMap<String, String> r1 = new HashMap<String, String>();
//		r1.put("m", "crp1");
//		HashMap<String, String> r2 = new HashMap<String, String>();
//		r2.put("m", "crp2");
//		HashMap<String, String> r3 = new HashMap<String, String>();
//		r3.put("m", "crp3");
//		replacements.add(new Replace(r1, 0));
//		replacements.add(new Replace(r2, 0));
//		replacements.add(new Replace(r3, 0));
//
//		System.out.println(convertFormat("%a $m $n $p $([$m $n $p][, $m $n $p][, and $m $n $p])x", "m", "dadsf", "n", "nnnn", "p", "opop", "x", replacements));
//
//		if(true) return;
		String toComment = new String(Files.readAllBytes(new File(args[0]).toPath()), "UTF-8");
		CompilationUnit unit = JavaParser.parse(toComment);
		for(ImportDeclaration imports : unit.getImports()) {
			if(imports.isAsterisk()) {
				if(imports.isStatic()) {
					imports.setComment(newComment("import.static_asterisk", "c", imports.getNameAsString()));
				} else {
					imports.setComment(newComment("import.asterisk", "p", imports.getNameAsString()));
				}
			} else {
				if(imports.isStatic()) {
					String str = imports.getNameAsString();
					int ind = str.lastIndexOf(".");
					if(ind == -1) {
						imports.setComment(newComment("import.static_fallback", "f", str));
					} else {
						imports.setComment(newComment("import.static", "f", str.substring(ind + 1), "c", str.substring(0, ind)));
					}
				} else {
					imports.setComment(newComment("import.normal", "c", imports.getNameAsString()));
				}
			}
		}
		for(TypeDeclaration<?> types : unit.getTypes()) {
			if(((ClassOrInterfaceDeclaration)types).isInterface()) {
				types.setComment(newComment("class.new_interface", "m", stringModifiers(types.getModifiers()), "c", types.getNameAsString()));
			} else {
				types.setComment(newComment("class.new_class", "m", stringModifiers(types.getModifiers()), "c", types.getNameAsString()));
			}
			for(FieldDeclaration field : types.getFields()) {
				if(field.getVariables().size() > 1) {
					ArrayList<Replace> repl = new ArrayList<Replace>();
					for(VariableDeclarator var : field.getVariables()) {
						Optional<Expression> initializer = var.getInitializer();
						if(initializer.isPresent()) {
							HashMap<String, String> re = new HashMap<>();
							re.put("n", var.getNameAsString());
							re.put("v", initializer.get().toString());
							repl.add(new Replace(re, 1));
						} else {
							HashMap<String, String> re = new HashMap<>();
							re.put("n", var.getNameAsString());
							repl.add(new Replace(re, 0));
						}
					}
					field.setComment(newComment("field.new_field.multiple", "n", Integer.toString(field.getVariables().size()), "m", stringModifiers(field.getModifiers()), "t", stringType(field.getCommonType()), "f", repl));
				} else {
					VariableDeclarator var = field.getVariable(0);
					Optional<Expression> initializer = var.getInitializer();
					if(initializer.isPresent()) {
						field.setComment(newComment("field.new_field.single_value", "m", stringModifiers(field.getModifiers()), "t", stringType(field.getCommonType()), "f", var.getNameAsString(), "v", initializer.get().toString()));
					} else {
						field.setComment(newComment("field.new_field.single_nvalue", "m", stringModifiers(field.getModifiers()), "t", stringType(field.getCommonType()), "f", var.getNameAsString()));
					}
				}
			}
			for(MethodDeclaration method : types.getMethods()) {
				NodeList<Parameter> parameters = method.getParameters();
				NodeList<ReferenceType> exceptions = method.getThrownExceptions();
				ArrayList<Replace> exrepl = new ArrayList<>();
				for(ReferenceType exception : exceptions) {
					HashMap<String, String> rep = new HashMap<>();
					rep.put("x", exception.asString());
					exrepl.add(new Replace(rep, 0));
				}
				if(parameters.size() == 0) {
					method.setComment(newComment("method.new_method.no_param" + (exceptions.size() == 0 ? "" : "_ex"), "n", method.getNameAsString(), "m", stringModifiers(method.getModifiers()), "r", stringType(method.getType()), "e", exrepl));
				} else {
					ArrayList<Replace> repl = new ArrayList<>();
					for(Parameter param : parameters) {
						HashMap<String, String> r = new HashMap<>();
						r.put("t", stringType(param.getType()));
						r.put("n", param.getNameAsString());
						repl.add(new Replace(r, 0));
					}
					method.setComment(newComment("method.new_method.param" + (exceptions.size() == 0 ? "" : "_ex"), "n", method.getNameAsString(), "m", stringModifiers(method.getModifiers()), "r", stringType(method.getType()), "p", repl, "e", exrepl));
				}

				Optional<BlockStmt> body = method.getBody();

				if(body.isPresent()) {
					commentBlock(body.get());
				}
			}
		}
		System.out.println(unit);
//		System.out.println(classA.getMethods().get(0).getBody().get().getStatements().get(0).asExpressionStmt().getExpression().asVariableDeclarationExpr().getVariables().get(0).getName());

	}

	public static void commentBlock(BlockStmt block) {
		for(Statement stmt : block.getStatements()) {
			if(stmt.isAssertStmt()) {
				Optional<Expression> msg = stmt.asAssertStmt().getMessage();
				if(msg.isPresent()) {
					stmt.setComment(newComment("stmt.assert_msg", "e", stmt.asAssertStmt().getCheck(), "m", msg.get()));
				} else {
					stmt.setComment(newComment("stmt.assert", "e", stmt.asAssertStmt().getCheck()));
				}
			} else if(stmt.isBlockStmt()) {
				commentBlock(stmt.asBlockStmt());
			} else if(stmt.isBreakStmt()) {
				Optional<SimpleName> lbl = stmt.asBreakStmt().getLabel();
				if(lbl.isPresent()) {
					stmt.setComment(newComment("stmt.break_label", "l", lbl.get().asString()));
				} else {
					stmt.setComment(newComment("stmt.break"));
				}
			} else if(stmt.isContinueStmt()) {

			} else if(stmt.isDoStmt()) {

			} else if(stmt.isEmptyStmt()) {

			} else if(stmt.isExplicitConstructorInvocationStmt()) {

			} else if(stmt.isExpressionStmt()) {

			} else if(stmt.isForStmt()) {

			} else if(stmt.isForeachStmt()) {

			} else if(stmt.isIfStmt()) {

			} else if(stmt.isLabeledStmt()) {
				stmt.setComment(newComment("stmt.label", "l", stmt.asLabeledStmt().getLabel().asString()));
				commentBlock(stmt.asLabeledStmt().getStatement().asBlockStmt());
			} else if(stmt.isLocalClassDeclarationStmt()) {

			} else if(stmt.isReturnStmt()) {
				Optional<Expression> expr = stmt.asReturnStmt().getExpression();
				if(expr.isPresent()) {
					stmt.setComment(newComment("stmt.return_v", "v", expr.get()));
				} else {
					stmt.setComment(newComment("stmt.return"));
				}
			} else if(stmt.isSwitchEntryStmt()) {

			} else if(stmt.isSynchronizedStmt()) {

			} else if(stmt.isThrowStmt()) {
//				stmt.setComment(newComment(stmt.asThrowStmt().getExpression()
			} else if(stmt.isTryStmt()) {

			} else if(stmt.isUnparsableStmt()) {

			} else if(stmt.isWhileStmt()) {

			}
		}
	}
}
