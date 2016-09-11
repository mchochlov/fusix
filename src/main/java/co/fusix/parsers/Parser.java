package co.fusix.parsers;

import java.io.InputStream;
import java.util.Set;

import co.fusix.component.Component;
import co.fusix.corpus.Granularity;
import co.fusix.exceptions.CorpusParserException;

public interface Parser {

	Set<Component> parse(InputStream in, 
			String path,
			Granularity granularity, 
			boolean includeContent) throws CorpusParserException;

}
