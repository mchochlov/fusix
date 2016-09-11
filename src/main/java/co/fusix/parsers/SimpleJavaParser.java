package co.fusix.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import co.fusix.component.Component;
import co.fusix.component.GenericComponent;
import co.fusix.corpus.Granularity;
import co.fusix.exceptions.CorpusParserException;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ModifierSet;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public final class SimpleJavaParser implements Parser {

	private final Path srcDir;
	public SimpleJavaParser(final Path srcDir) {this.srcDir = srcDir;}

	@Override
	public Set<Component> parse(InputStream in, String path, 
			Granularity granularity,
			boolean includeContent) throws CorpusParserException 
	{
		try {
			switch(granularity) {
			case FILE:
				return parseFile(in, path, includeContent);
			case METHOD:
				return parseMethod(in, path, includeContent);
			default:
				throw new IllegalArgumentException("Granularity level not supported.");
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new CorpusParserException();
		}
	}


	@Override
	public String toString() {
		return "SimpleJavaParser [srcDir=" + srcDir + "]";
	}

	private Set<Component> parseMethod(InputStream in, String path, boolean includeContent) throws IOException {

		Set<Component> components = new HashSet<>();

		VoidVisitorAdapter<String> visitor = new VoidVisitorAdapter<String>() {

			@Override
			public void visit(MethodDeclaration md, String path) {
				super.visit(md, path);
		
				if(!(md.getParentNode() instanceof ObjectCreationExpr) && // not anonymous class method
						!ModifierSet.isAbstract(md.getModifiers())) //not abstract
				{ 
					if (!includeContent) {
						components.add(new GenericComponent(getMethodDeclaration(path, md), 
								md.getBeginLine(), md.getEndLine()));
					} else {
						components.add(new GenericComponent(getMethodDeclaration(path, md), 
								md.toString(), md.getBeginLine(), md.getEndLine()));
					}
				}
			}

		};
		
		try {
			visitor.visit(JavaParser.parse(in), path);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return components;
	}

	private Set<Component> parseFile(InputStream in, String path,
			boolean includeContent) throws IOException {
		if (!includeContent) {
			return Collections.singleton(new GenericComponent(path));
		}

		String content = IOUtils.toString(in);
		int index = content.indexOf("*/");
		if (index != -1) {
			String license = content.substring(0, index + 2).toLowerCase();
			if (license.contains("license")) {
				content = content.substring(index + 2);
			}
		}
		return Collections.singleton(new GenericComponent(path, content));
	}
	
	private static String getMethodDeclaration(String path, MethodDeclaration n){
		String name = "";
		if (n.getParentNode() instanceof ClassOrInterfaceDeclaration) {
			ClassOrInterfaceDeclaration parent = (ClassOrInterfaceDeclaration) n.getParentNode();
			name = parent.getName();
		} else if (n.getParentNode() instanceof EnumConstantDeclaration) {
			EnumConstantDeclaration parent = (EnumConstantDeclaration) n.getParentNode();
			name = parent.getName();
		}
		
		String declaration = name + "_" + n.getDeclarationAsString(false,  false,  false);
		declaration = declaration.replaceFirst(" ", "_");//substring(declaration.indexOf(" ") + 1); // remove return type
		declaration = declaration.replaceAll("\\s+", "");// remove whitespaces
		return path + "::" + declaration;
	}
	
}
