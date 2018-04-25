package co.fusix.versioncontrol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import co.fusix.component.Component;
import co.fusix.corpus.Granularity;
import co.fusix.corpus.Source;
import co.fusix.exceptions.VersionControlException;
import co.fusix.utils.Utils;

public final class GitVersionControl implements VersionControl<BlobWalk> {

	private static final long COMMIT_PERIOD_DAYS = 10;
	private static final String WHITESPACE = " ";
	private static final String JAVA_SUFFIX = "java";
	private final String revision;
	private final Path srcDir;
	private final AnyObjectId objectId;
	private final Git git;
	private final boolean filtered;
	private Map<String, Set<RevCommit>> clusterLookup = new HashMap<>();
	private final DiffConfig diffConfig;
	private static Map<String, Set<RevCommit>> artefacts = new HashMap<>();
	
	public GitVersionControl(final Path srcDir, final String revision, boolean filtered) {
		this.srcDir = srcDir;
		this.revision = revision;
		this.filtered = filtered;
		try {
			git = Git.open(this.srcDir.toFile());
			objectId = git.getRepository().resolve(revision);
			Config config = new Config(git.getRepository().getConfig());
		    config.setString("diff", null, "renames", "copies");
		    config.setInt("diff", null, "renameLimit", Integer.MAX_VALUE);
		    diffConfig = config.get(DiffConfig.KEY);

			try(RevWalk rw = new RevWalk(git.getRepository())){
				findBranches(rw.parseCommit(objectId), rw);
				System.out.println("Clusters # " + clusterLookup.values().size());
				//clusterLookup.values().forEach(System.out::println);
			}


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
		if (granularity != Granularity.FILE && granularity != Granularity.METHOD) {
			throw new IllegalArgumentException("Granularity level not supported.");
		}
		
		try (RevWalk revWalk = new RevWalk(git.getRepository())){
			
			String filePath = null;
			BlameResult br = null;
			for (Component component : components) {
				if (recentness != Recentness.ALL){
					if (filePath == null || !filePath.equals(component.getFilePath())) {
						br = git.blame()
								.setStartCommit(objectId)
								.setFollowFileRenames(true)
								.setFilePath(component.getFilePath())
								.call();
						filePath = component.getFilePath();
					}
				}
				
				System.out.println(component.getPath());
				Set<RevCommit> commits = new HashSet<>();
				if (recentness == Recentness.RECENT) {
					commits = getRecentCommitsComponent(component, br);
				} else if (recentness == Recentness.ALL) {
					commits = getAllCommitsComponent(revWalk, component, granularity);	
				}else if (recentness == Recentness.RECENT_CR){	    	
			    	commits = getCRRecent(component, revWalk, granularity, br);
			    }
				
				component.addContent(getContent(commits));
			}
		} catch (IOException | GitAPIException e) {
			e.printStackTrace();
			throw new VersionControlException();
		}
	}

	
	private String getContent(Set<RevCommit> sc){
		if (filtered) {
			Predicate<RevCommit> mc = new FunctionalCommit();
			return String.join(WHITESPACE, sc.parallelStream()
					.filter(mc)
					.filter(x -> x.getParentCount() == 1)
					.map(RevCommit::getFullMessage).collect(Collectors.toList()));
		} else {
			return String.join(WHITESPACE, sc.parallelStream()
				.map(RevCommit::getFullMessage).collect(Collectors.toList()));
		}
		
	}
	
	
	private Set<RevCommit> getCRRecent(Component component, RevWalk revWalk, Granularity granularity, BlameResult br) throws MissingObjectException, IncorrectObjectTypeException, IOException, GitAPIException{
		Set<RevCommit> commits = new HashSet<>();
		List<RevCommit> tempR = getRecentCommitsComponent(component, br)
					.stream()
					.sorted(Comparator.comparing(x -> ((RevCommit) x).getAuthorIdent().getWhen()).reversed())
					.collect(Collectors.toList());
		
		commits.addAll(tempR);
		
		List<RevCommit> tempA = null;
		Set<RevCommit> tempAset = null;
		try{
			tempAset = getAllCommitsComponent(revWalk, component, granularity);
			tempA = tempAset
				.stream()
				.sorted(Comparator.comparing(x -> ((RevCommit) x).getAuthorIdent().getWhen()).reversed())
				.collect(Collectors.toList());
		} catch (NullPointerException npe){
			npe.printStackTrace();
			tempAset = new HashSet<>();
			tempA = new ArrayList<>();
		}
		
		for (RevCommit rc : tempR){
			if (clusterLookup.containsKey(rc.name())){
	    		for (RevCommit c : clusterLookup.get(rc.name())){
	    			if (tempAset.contains(c)){
	    				commits.add(c);
	    			}
	    		}
	    		continue;
			}
			
		/*	Iterator<RevCommit> rcIter = tempA.iterator();
			while(rcIter.hasNext()){
				RevCommit ra = rcIter.next();
				Instant rci = rc.getAuthorIdent().getWhen().toInstant();
				Instant rca = ra.getAuthorIdent().getWhen().toInstant();
				if (!rc.name().equals(ra.name()) 
						&& rc.getAuthorIdent().getName().equals(ra.getAuthorIdent().getName()) 
						&& Duration.between(rci,  rca).abs().toDays() <= COMMIT_PERIOD_DAYS){
					commits.add(ra);
					rc = ra;
				} else if (rc.name().equals(ra.name()) || rci.isBefore(rca)){
					continue;
				} else {
					break;
				}
			}*/
		}
		return commits;
	}

	private Set<RevCommit> getRecentCommitsComponent(Component component, BlameResult blame) throws GitAPIException{
		Set<RevCommit> result = new HashSet<>();
		if (blame == null) throw new NullPointerException(this.toString() + component);
		
		int start = component.getStartLine() == -1 ? 1 : component.getStartLine();
		int end = component.getEndLine() == -1 ? blame.getResultContents().size() : component.getEndLine();
		for (int i = start; i <= end; i++) {
			result.add(blame.getSourceCommit(i - 1));
		}
		return result;
	}
	
	private Set<RevCommit> getAllCommitsComponent(RevWalk revWalk, Component component, Granularity granularity) throws MissingObjectException, IncorrectObjectTypeException, IOException{

		//if (artefacts.containsKey(component.getFilePath())) return artefacts.get(component.getFilePath());
		
		revWalk.reset();
	    revWalk.markStart(revWalk.parseCommit(objectId));
	    revWalk.setTreeFilter(FollowFilter.create(component.getFilePath(), diffConfig));

	    List<RevCommit> qCommits = StreamSupport.stream(
				revWalk.spliterator(), true).collect(
				Collectors.toList());
	    //artefacts.put(component.getFilePath(), qCommits.stream().collect(Collectors.toSet()));
	    if (granularity == Granularity.FILE){
	    	return qCommits.stream().collect(Collectors.toSet());
	    }
	    
	    Set<RevCommit> commits = new HashSet<>();
/*	    OutputStream outputStream = DisabledOutputStream.INSTANCE;
		try (DiffFormatter formatter = new DiffFormatter(outputStream)) {
			formatter.setRepository(git.getRepository());
			formatter.setPathFilter(FollowFilter.create(component.getFilePath(), diffConfig));
			int startLine = component.getStartLine() - 1;
			int endLine = component.getEndLine() - 1;
				
			for (int i = 0; i < qCommits.size(); i++) {
				//special case last commit
				if (i == qCommits.size() - 1){
					commits.add(qCommits.get(i));
					break;
				}
				List<DiffEntry> entries = formatter.scan(qCommits.get(i + 1).getTree(), qCommits.get(i).getTree()); //A vs B
				if (entries != null && !entries.isEmpty()){
					FileHeader fileHeader = formatter.toFileHeader(entries.get(0));
					int tstartLine = startLine;
					int tendLine = endLine;
					for (Edit edit : fileHeader.toEditList()) {
						//special case method added
						if (edit.getType() == Type.INSERT && edit.getBeginB() <= startLine && edit.getEndB() >= endLine
								&& edit.getLengthB() - edit.getLengthA() > 1){
							commits.add(qCommits.get(i));
							i = qCommits.size(); //hack to break the nested loop
							break;
						}
						// check overlap of regionB and start end
						if ((edit.getBeginB() >= startLine && edit.getBeginB() <= endLine)
								|| (edit.getEndB() >= startLine && edit.getEndB() <= endLine)
								|| (edit.getBeginB() <= startLine && edit.getEndB() >= endLine)) 
						{
							commits.add(qCommits.get(i));										
						}
						// adjust position
						if (startLine > edit.getEndB()) {
							tstartLine -= edit.getLengthB() - edit.getLengthA();
							tendLine -= edit.getLengthB() - edit.getLengthA();
						} else if (startLine <= edit.getBeginB() && endLine >= edit.getBeginB()) {
							tendLine -= edit.getLengthB() - edit.getLengthA();
						}
					}
					startLine = tstartLine;
					endLine = tendLine;
				}

			}
		}
*/		for (String string : historicalStats(component.getStartLine(), component.getEndLine(), component.getFilePath())) {
			try {
				commits.add(revWalk.parseCommit(git.getRepository().resolve(string)));
			} catch (MissingObjectException e){
				System.err.println("Missing object: " + string + " " + component.getPath());
			}
		}
	    return commits;
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
	
	private void findBranches(RevCommit head, RevWalk revWalk) throws IOException{
		Stack<RevCommit> stack = new Stack<>();
		Set<String> visited = new HashSet<>();
		
		stack.push(head);
		try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)){
			df.setRepository(git.getRepository());
			df.setDetectRenames(true);
			df.setPathFilter(PathSuffixFilter.create("java"));
			Set<RevCommit> cluster = new HashSet<>();
			cluster.add(head);
			while(!stack.isEmpty()){
				RevCommit rc = stack.pop();
				if (!visited.contains(rc.name())){
					if (rc.getParentCount() == 0) {
						//doDiff(rc, null, df);
					} else if (rc.getParentCount() == 1){
						RevCommit parent = revWalk.parseCommit(rc.getParent(0).getId());
						//doDiff(rc, parent, df);
						stack.push(parent);
					} else if (rc.getParentCount() == 2) {
						stack.push(revWalk.parseCommit(rc.getParent(1).getId()));
						stack.push(revWalk.parseCommit(rc.getParent(0).getId()));
					}
					visited.add(rc.name());
					cluster.add(rc);
				} else {
					//skip master && branches smaller than 2
					if (!cluster.contains(head) && cluster.size() > 1 && cluster.size() <= 50){
						for (RevCommit r : cluster){
							clusterLookup.put(r.name(), cluster);
						}
					}
					cluster = new HashSet<>();
				}
			}
		}
	}
	
	@Override
	public String toString() {
		return "GitVersionControl [revision=" + revision + ", srcDir=" + srcDir
				+ ", objectId=" + objectId + ", git=" + git + "]";
	}

	private List<String> historicalStats(int beginLine, int endLine, String file2) {
		List<String> commits = new ArrayList<>();
		final ProcessBuilder pb = new ProcessBuilder();
		pb.directory(srcDir.toFile());
		pb.redirectErrorStream(true);
		pb.command("git", "log", revision, "--", "-L" + beginLine + "," + endLine + ":" + file2,
				"--follow", file2);

		// pb.redirectOutput(Redirect.INHERIT);
		Process gitLog = null;
		try {
			gitLog = pb.start();

			// gitLog.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(gitLog.getInputStream()));
			Pattern p = Pattern.compile("\\b[0-9a-f]{40}\\b");
			String line = null;
			while ((line = reader.readLine()) != null) {
				Matcher m = p.matcher(line);
				if (m.find()) {
					commits.add(m.group());
				}
			}
			return commits;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return commits;
	}

}
