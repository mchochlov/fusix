package co.fusix.project;

import java.util.List;

import co.fusix.corpus.Corpus;

public interface Project<T> {
	
	void add(Corpus<T> corpus);
	
	List<Corpus<T>> getCorpora();
}
