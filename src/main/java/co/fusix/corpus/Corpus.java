package co.fusix.corpus;

import java.util.Set;
import java.util.concurrent.Callable;

import co.fusix.component.Component;

public interface Corpus <T> {
	
	Callable<T> create();
	
	Callable<T> update();
	
	Callable<T> delete();
	
	Callable<Set<Component>> search(String query);
}
