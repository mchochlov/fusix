package co.fusix.versioncontrol;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

public class BlobWalk extends TreeWalk {
	
	private final Repository repo;
	
	public BlobWalk(Repository repo) {
		super(repo);
		this.repo = repo;
	}

	public InputStream getBlob() throws IOException{
		try (InputStream in = repo.open(this.getObjectId(0), Constants.OBJ_BLOB).openStream()) {
			return in;
		}
	}
	
}
