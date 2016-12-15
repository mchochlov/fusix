package co.fusix.versioncontrol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import co.fusix.component.Component;
import co.fusix.corpus.Granularity;
import co.fusix.corpus.Source;
import co.fusix.exceptions.VersionControlException;
import co.fusix.utils.Utils;

public final class GitVersionControl implements VersionControl<BlobWalk> {

	private static final String WHITESPACE = " ";
	private static final String JAVA_SUFFIX = "java";
	private final String revision;
	private final Path srcDir;
	private final AnyObjectId objectId;
	private final Git git;
	private final boolean filtered;
	
	public GitVersionControl(final Path srcDir, final String revision, boolean filtered) {
		this.srcDir = srcDir;
		this.revision = revision;
		this.filtered = filtered;
		try {
			git = Git.open(this.srcDir.toFile());
			objectId = git.getRepository().resolve(revision);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot construct version control object wrapper.");
		}
	}
	
	
	@Override
	public void close() throws IOException {
		if (git != null) git.close();
	}

	@Override
	public void annotateAll(Set<Component> components, Recentness recentness, 
			Granularity granularity) throws VersionControlException
	{
		try {
			switch(granularity) {
			case FILE:
				annotateAllFiles(components, recentness);
				break;
			case METHOD:
				annotateAllMethods(components, recentness);
				break;
			default:
				throw new IllegalArgumentException("Granularity level not supported.");
			}
		} catch (IOException | GitAPIException e) {
			e.printStackTrace();
			throw new VersionControlException();
		}
	}

	private void annotateAllMethods(Set<Component> components,
			Recentness recentness) throws IOException, GitAPIException {
		
		for (Component component : components) {
			
			Set<RevCommit> commits = new HashSet<>();
			if (recentness == Recentness.RECENT) {
				BlameResult blame = git.blame().setStartCommit(objectId)
									.setFollowFileRenames(true)
									.setFilePath(component.getFilePath()).call();
							
				if (blame == null) throw new NullPointerException(this.toString());

				for (int i = component.getStartLine(); i <= component.getEndLine(); i++) {
					commits.add(blame.getSourceCommit(i - 1));
				}

			} else if (recentness == Recentness.ALL) {
				// walk the revision tree and find only those commits where file
				// is modified
				// for each relevant commit check if method was touched -> add
				// changeset and
				// modify function position if needed
				Config config = new Config(git.getRepository().getConfig());
				config.setString("diff", null, "renames", "copies");
				config.setInt("diff", null, "renameLimit", Integer.MAX_VALUE);
				DiffConfig diffConfig = config.get(DiffConfig.KEY);

				try (RevWalk revWalk = new RevWalk(git.getRepository())) {
					revWalk.markStart(revWalk.parseCommit(objectId));
					revWalk.setTreeFilter(FollowFilter.create(component.getFilePath(), diffConfig));
					Queue<RevCommit> qCommits = StreamSupport.stream(
							revWalk.spliterator(), true).collect(
							Collectors.toCollection(LinkedList::new));
										
					OutputStream outputStream = DisabledOutputStream.INSTANCE;
					try (DiffFormatter formatter = new DiffFormatter(outputStream)) {
						formatter.setRepository(git.getRepository());
						formatter.setPathFilter(PathFilter.create(component.getFilePath()));
						int startLine = component.getStartLine() - 1;
						int endLine = component.getEndLine() - 1;
						
						RevCommit next = qCommits.poll();
						//HACK
						//commits.add(next);
						//
						while (next != null) {
							RevCommit prev = qCommits.poll();
							RevTree prevTree = prev == null ? null : prev.getTree();
							List<DiffEntry> entries = formatter.scan(prevTree, next.getTree());
								
							if (entries != null && !entries.isEmpty()){
								FileHeader fileHeader = formatter.toFileHeader(entries.get(0));
								// fileHeader.toEditList().forEach(System.out::println);
								int tstartLine = startLine;
								int tendLine = endLine;
								for (Edit edit : fileHeader.toEditList()) {
									// check overlap of regionB and start end
									if ((startLine >= edit.getBeginB() && startLine <= edit.getEndB())
											|| (endLine >= edit.getBeginB() && endLine <= edit.getEndB())) {
										commits.add(next);
									}
									// adjust position
									// if a method is located after change or is
									// included in a change
									if (startLine >= edit.getBeginB()) {
										switch (edit.getType()) {
										case REPLACE:
											startLine -= edit.getLengthB() - edit.getLengthA();
											endLine -= edit.getLengthB() - edit.getLengthA();
											break;
										case DELETE:
											startLine += edit.getLengthA();
											endLine += edit.getLengthA();
											break;
										case INSERT:
											startLine -= edit.getLengthB();
											endLine -= edit.getLengthB();
											break;
										case EMPTY:
										default:
											throw new IllegalStateException("Unknown diff");
										}
									} else if (startLine < edit.getBeginB()	&& endLine >= edit.getBeginB()) {
										switch (edit.getType()) {
										case REPLACE:
											endLine -= edit.getLengthB() - edit.getLengthA();
											break;
										case DELETE:
											endLine += edit.getLengthA();
											break;
										case INSERT:
											endLine -= edit.getLengthB();
											break;
										case EMPTY:
										default:
											throw new IllegalStateException("Unknown diff");
										}
									}
								}
								startLine = tstartLine;
								endLine = tendLine;
							}

							next = prev;
						}
					}
				}
				//System.out.println(component.toString() + component.getStartLine() + "," + component.getEndLine());
				//commits.forEach(System.out::println);
			
			}
			
			String content;
			if (filtered) {
				Predicate<RevCommit> mc = new FunctionalCommit();
				content = String.join(WHITESPACE, commits.parallelStream()
						.filter(mc)
						.map(RevCommit::getFullMessage).collect(Collectors.toList()));
			} else {
				content = String.join(WHITESPACE, commits.parallelStream()
					.map(RevCommit::getFullMessage).collect(Collectors.toList()));
			}
			component.addContent(content);
		}
	}


	@Override
	public String toString() {
		return "GitVersionControl [revision=" + revision + ", srcDir=" + srcDir
				+ ", objectId=" + objectId + ", git=" + git + "]";
	}


	private void annotateAllFiles(Set<Component> components,
			Recentness recentness) throws IOException, GitAPIException 
	{
		for (Component component : components) {
			Set<RevCommit> commits = new HashSet<>();
			if (recentness == Recentness.RECENT) {
				BlameResult blame = git.blame()
						.setStartCommit(objectId)
						.setFollowFileRenames(true)
						.setFilePath(component.getFilePath())
						.call();
				
				if (blame == null) throw new NullPointerException(this.toString() + component);
				
				for (int i = 0; i < blame.getResultContents().size(); i++) {
					commits.add(blame.getSourceCommit(i));
				}
				
				
			} else if (recentness == Recentness.ALL) {
				Config config = new Config(git.getRepository().getConfig());
			    config.setString("diff", null, "renames", "copies");
			    config.setInt("diff", null, "renameLimit", Integer.MAX_VALUE);
			    DiffConfig diffConfig = config.get(DiffConfig.KEY);
			    try(RevWalk revWalk = new RevWalk(git.getRepository())){
			    	revWalk.markStart(revWalk.parseCommit(objectId));
			    	revWalk.setTreeFilter(FollowFilter.create(component.getPath(), diffConfig));
			    	commits = StreamSupport.stream(revWalk.spliterator(), true).collect(Collectors.toSet());
			    }
			}

			String content;
			if (filtered) {
				Predicate<RevCommit> mc = new FunctionalCommit();
				content = String.join(WHITESPACE, commits.parallelStream()
						.filter(mc)
						.map(RevCommit::getFullMessage).collect(Collectors.toList()));
			} else {
				content = String.join(WHITESPACE, commits.parallelStream()
					.map(RevCommit::getFullMessage).collect(Collectors.toList()));
			}
			component.addContent(content);
		}
	}


	@Override
	public BlobWalk workingTree() throws VersionControlException {
		try (RevWalk revWalk = new RevWalk(git.getRepository());
        		BlobWalk blobWalk = new BlobWalk(git.getRepository())) 
        {
        	RevCommit threshold = revWalk.parseCommit(objectId);
        	RevTree tree = threshold.getTree();
            blobWalk.addTree(tree);
            blobWalk.setRecursive(true);
            blobWalk.setFilter(PathSuffixFilter.create(JAVA_SUFFIX));
            return blobWalk;
        } catch (IOException e) {
        	throw new VersionControlException();
		}
	}

	static class FunctionalCommit implements Predicate<RevCommit> {

		private static final Set<String> MAINTENANCE_TOKENS = Stream.of("clean", "licens", "merg", 
				"releas", "structur", "integr", "copyright", "document", "manual", "javadoc", "comment", "migrat",
				"repositori", "code", "review", "polish", "upgrad", "style", "format", "organ", "todo")
				.collect(Collectors.toSet());
		
		@Override
		public boolean test(RevCommit commit) {
			Set<String> commitTokens;
			try {
				commitTokens = Utils.preprocessString(commit.getFullMessage(), Utils.getAnalyzer(Source.VCS))
						.stream().collect(Collectors.toSet());
				if (commitTokens.size() < 3) {
					return false;
				} else {
					commitTokens.retainAll(MAINTENANCE_TOKENS);
					if (commitTokens.size() != 0) {
						return false;
					}
				}
			} catch(IOException e){
				e.printStackTrace();
			}
			return true;
		}

	}
}
