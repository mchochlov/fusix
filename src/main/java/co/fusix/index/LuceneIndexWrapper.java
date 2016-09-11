package co.fusix.index;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import co.fusix.component.Component;
import co.fusix.component.GenericComponent;
import co.fusix.corpus.Source;
import co.fusix.exceptions.IndexException;
import co.fusix.utils.Utils;

public final class LuceneIndexWrapper implements Index {
	
	private enum Mode {READ, WRITE};
	
	private static final int MAX_HITS = 5000;
	private static final String QUERY_FIELD_NAME = "query";
	private final Path indexDir;
	private final Source source;
	private final IndexWriter writer;
	private final IndexReader reader;
	private final Analyzer analyzer;
	private final Directory dir;
	
	private LuceneIndexWrapper(final Path indexDir, final Source source, final Mode mode) {
		this.indexDir = indexDir;
		this.source = source;
		try {
			this.dir = FSDirectory.open(indexDir);
			this.analyzer = Utils.getAnalyzer(source);
			if (mode == Mode.WRITE) {
				IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setOpenMode(OpenMode.CREATE);	
				writer = new IndexWriter(dir, iwc);
				reader = null;
			} else if (mode == Mode.READ) {
				reader = DirectoryReader.open(dir);
				writer = null;
			} else {
				throw new IllegalArgumentException();
			}

		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalStateException("Cannot construct Lucene index.");
		}
	}
		
	@Override
	public void close() throws IOException {
		//analyzer.close();
		//dir.close();
		if (writer != null && writer.isOpen())	{
			writer.close();
		}
		if (reader != null) reader.close();
		
	}

	@Override
	public void writeAll(final Set<Component> components) throws IndexException{
		try {
			for(Component component: components) {
				Document luceneDoc = new Document();
				luceneDoc.add(new StringField(Component.Fields.PATH.name(), component.getPath(), Field.Store.YES));
				luceneDoc.add(new TextField(Component.Fields.CONTENT.name(), component.getContent(), Field.Store.NO));
				writer.addDocument(luceneDoc);
			}
		} catch (IOException e) {
			throw new IndexException();
		}		
	}


	@Override
	public String toString() {
		return "LuceneIndexWrapper [indexDir=" + indexDir + ", source="
				+ source// + ", writer=" + writer + ", analyzer=" + analyzer
				+ "]";
	}

	@Override
	public Set<Component> search(String queryString) throws IndexException {
		try {
			final Set<Component> components = new HashSet<>();
			IndexSearcher searcher = new IndexSearcher(reader);
			
			BooleanQuery query = new BooleanQuery();
			for (String word : getQueryTokens(queryString, analyzer)) {
				query.add(new TermQuery(new Term(Component.Fields.CONTENT.name(), word)), Occur.SHOULD);
			}
	
			TopDocs results = searcher.search(query, MAX_HITS);
			ScoreDoc[] hits = results.scoreDocs;
		
			for (int i = 0; i < hits.length; i++) {
				components.add(new GenericComponent(searcher.doc(hits[i].doc)
						.get(Component.Fields.PATH.name())));
			}
			return components;
		} catch (IOException e) {
			e.printStackTrace();
			throw new IndexException();
		}
	}

	@Override
	public void deleteAll() throws IndexException {
		try {
			writer.deleteAll();
		} catch (IOException e) {
			throw new IndexException();
		}
	}
			
	private static List<String> getQueryTokens(String string, Analyzer analyzer) throws IOException {
		List<String> tokens = new ArrayList<String>();
		try (TokenStream tokenizer = analyzer.tokenStream(QUERY_FIELD_NAME, string)){
			CharTermAttribute token = tokenizer.getAttribute(CharTermAttribute.class);
			tokenizer.reset();

			while (tokenizer.incrementToken()) {
				tokens.add(token.toString());
			}
			tokenizer.end();
			return tokens;
		}
	}
	
	public static Index newWriteableInstance(Path indexDir, Source source) {
		return new LuceneIndexWrapper(indexDir, source, Mode.WRITE);
	}

	public static Index newReadableInstance(Path indexDir, Source source) {
		return new LuceneIndexWrapper(indexDir, source, Mode.READ);
	}

}
