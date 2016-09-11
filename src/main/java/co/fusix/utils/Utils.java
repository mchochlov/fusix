package co.fusix.utils;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import co.fusix.corpus.Source;

public final class Utils {

	private Utils(){}
	
	public static Analyzer getAnalyzer(Source source) throws IOException{
		switch(source) {
		case CODE:
			return CustomAnalyzer.builder()
					.addCharFilter("patternreplace", "pattern", "(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])", "replacement", " ")
					.withTokenizer("lowercase")
				
					.addTokenFilter("stop", "ignoreCase", "true", "words", "java_keywords.txt", "format", "snowball")
					.addTokenFilter("porterstem")
					.addTokenFilter("stop", "ignoreCase", "true")//, "words", "stopwords.txt", "format", "wordset")
					//.addTokenFilter("length", "min", "3", "max", "255")
					.build();
		case VCS:
		case BOTH:
			return CustomAnalyzer.builder()
					.addCharFilter("patternreplace", "pattern", "(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])", "replacement", " ")
					.withTokenizer("lowercase")
				
					//.addTokenFilter("stop", "ignoreCase", "true", "words", "java_keywords.txt", "format", "snowball")
					.addTokenFilter("porterstem")
					.addTokenFilter("stop", "ignoreCase", "true")//, "words", "stopwords.txt", "format", "wordset")
					//.addTokenFilter("length", "min", "3", "max", "255")
					.build();
		default:
			throw new IllegalArgumentException("Cannot instantiate analyzer for given source type.");
			
		}
	}
	
	public static List<String> preprocessString(String string, Analyzer analyzer) {
		List<String> tokens = new ArrayList<String>();
		try (TokenStream tokenizer = analyzer.tokenStream("test", string)){
			CharTermAttribute token = tokenizer.getAttribute(CharTermAttribute.class);
			tokenizer.reset();

			while (tokenizer.incrementToken()) {
				tokens.add(token.toString());
			}

			tokenizer.end();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return tokens;
	}

}
