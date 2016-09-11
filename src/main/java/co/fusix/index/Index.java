package co.fusix.index;

import java.io.Closeable;
import java.util.Set;

import co.fusix.component.Component;
import co.fusix.exceptions.IndexException;

public interface Index extends Closeable {
		
	void writeAll(Set<Component> components) throws IndexException;
	
	Set<Component> search(String query) throws IndexException;
	
	void deleteAll() throws IndexException;
}
