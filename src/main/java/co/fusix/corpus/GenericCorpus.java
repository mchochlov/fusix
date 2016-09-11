package co.fusix.corpus;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import co.fusix.component.Component;
import co.fusix.corpus.Configurations.Builder;
import co.fusix.index.Index;
import co.fusix.index.LuceneIndexWrapper;
import co.fusix.parsers.Parser;
import co.fusix.parsers.SimpleJavaParser;
import co.fusix.versioncontrol.BlobWalk;
import co.fusix.versioncontrol.GitVersionControl;
import co.fusix.versioncontrol.Recentness;
import co.fusix.versioncontrol.VersionControl;

class GenericCorpus implements Corpus<List<String>> {

	private final Path srcDir;
	private final Path indexDir;
	private final Granularity granularity;
	private final String revision;
	//model
	//language
	private final Source source;
	private final Recentness recentness;
	//vcs type
	private final boolean filtered;

	
	GenericCorpus(final Builder builder) {
		this.srcDir = builder.getSrcDir();
		this.indexDir = builder.getIndexDir();
		this.granularity = builder.getGranularity();
		this.revision = builder.getRevision();
		this.source = builder.getSource();
		this.recentness = builder.getRecentness();
		this.filtered = builder.isFiltered();
	}

	@Override
	public Callable<List<String>> create() {
		return () -> {
			try (VersionControl<BlobWalk> vc = new GitVersionControl(srcDir, revision, filtered);
					Index index = LuceneIndexWrapper.newWriteableInstance(indexDir, source) ) 
			{
				List<String> log = new ArrayList<>();
				log.add(this.toString());
				log.add(index.toString());
				
				Parser parser = new SimpleJavaParser(srcDir);
				log.add(parser.toString());
					
				boolean includeContent = source == Source.CODE || source == Source.BOTH ? true : false;

				int totalComponents = 0;
				BlobWalk blobWalk = vc.workingTree();
				while (blobWalk.next()) {
					InputStream in = blobWalk.getBlob();
					Set<Component> components = parser.parse(in, blobWalk.getPathString(), granularity, includeContent);
					if(source == Source.VCS || source == Source.BOTH)
						vc.annotateAll(components, recentness, granularity);
					
					
					index.writeAll(components);
					totalComponents += components.size();
				}
				log.add("Components size: " + totalComponents);
				
				return log;
			}
		};	
	}

	@Override
	public Callable<List<String>> update() {
		throw new UnsupportedOperationException("Corpus update operation is not currently implemented.");
	}

	@Override
	public Callable<Set<Component>> search(String query) {
		return () -> {
			try(Index index = LuceneIndexWrapper.newReadableInstance(indexDir, source)){
				return index.search(query);
			}
		};
	}

	@Override
	public String toString() {
		return "GenericCorpus [srcDir=" + srcDir + ", indexDir=" + indexDir
				+ ", granularity=" + granularity + ", revision=" + revision
				+ ", source=" + source + ", recentness=" + recentness
				+ ", filtered=" + filtered + "]";
	}

	@Override
	public Callable<List<String>> delete() {
		return () -> {
			try(Index index = LuceneIndexWrapper.newWriteableInstance(indexDir, source)){
				List<String> log = new ArrayList<>();
				log.add(this.toString());
				log.add(index.toString());

				index.deleteAll();
				return log;
			}
		};
	}

	
}
