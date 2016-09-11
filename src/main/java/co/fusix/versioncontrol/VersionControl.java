package co.fusix.versioncontrol;

import java.io.Closeable;
import java.util.Set;

import co.fusix.component.Component;
import co.fusix.corpus.Granularity;
import co.fusix.exceptions.VersionControlException;

public interface VersionControl<T>  extends Closeable{

	void annotateAll(final Set<Component> components, Recentness recentness, 
			Granularity granularity) throws VersionControlException;
	
	T workingTree() throws VersionControlException;
		
}
