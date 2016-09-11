package co.fusix.corpus;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import co.fusix.versioncontrol.Recentness;

public class Configurations {

	private Configurations(){}
	
	public static Builder builder(){
		return new Builder();
	}
	
	public static final class Builder {
		
		private static final String CURRENT_DIR = "";
		private static final String HEAD = "HEAD";
		
		private Path srcDir = Paths.get(CURRENT_DIR);
		private Path indexDir = Paths.get(CURRENT_DIR);
		private Granularity granularity = Granularity.METHOD;
		private String revision = HEAD; //?
		//model
		//language
		private Source source = Source.CODE;
		private Recentness recentness = Recentness.RECENT;
		//vcs type
		private boolean filtered = false;
		
		private Builder(){}
		
		public Builder srcDir(Path srcDir) { this.srcDir = srcDir; return this; }
		public Builder indexDir(Path indexDir) {this.indexDir = indexDir; return this; };
		public Builder granularity(Granularity granularity) {this.granularity = granularity; return this;}
		public Builder revision(String revision) {this.revision = revision; return this; }
		public Builder source(Source source) {this.source = source; return this;}
		public Builder recentness(Recentness recentness) {this.recentness = recentness; return this;}
		public Builder filtered() {this.filtered = true; return this;}
		
		Path getSrcDir() {return this.srcDir;};
		Path getIndexDir() {return this.indexDir;};
		Granularity getGranularity(){return this.granularity;};
		String getRevision(){return this.revision;};
		Source getSource(){return this.source;};
		Recentness getRecentness(){return this.recentness;};
		boolean isFiltered(){return this.filtered;};
		
		public Corpus<List<String>> build() {
			if (this.srcDir == null || this.indexDir == null || this.revision == null) {
				throw new NullPointerException("Source directory, index directory, or revision cannot be null.");
			}
			
			if (this.revision == "") { throw new IllegalStateException("Revision cannot have an empty value.");}
			
			return new GenericCorpus(this);
		}
	}
	
	public static Corpus<List<String>> baseline(){
		return new Builder().source(Source.CODE).build();	
	}

	public static Corpus<List<String>> acir(){
		return new Builder().source(Source.VCS).build();	
	}
	
	public static Corpus<List<String>> bacir(){
		return new Builder().source(Source.BOTH).build();
	}
}
