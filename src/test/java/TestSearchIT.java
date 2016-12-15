import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Before;
import org.junit.Test;

import co.fusix.component.Component;
import co.fusix.corpus.Configurations;
import co.fusix.corpus.Corpus;
import co.fusix.corpus.Granularity;
import co.fusix.corpus.Source;
import co.fusix.versioncontrol.Recentness;


public class TestSearchIT {

	ExecutorService executor;
	
    @Before 
    public void initialize() {
       executor = Executors.newFixedThreadPool(4);
    }
    
	@Test
	public void testAcirRecentNonFilteredMethod() throws IOException, URISyntaxException {
		
		int[][] truePositions = {{5,9,42},{25},{5,178,206,214,215,235,528,529,781,782,1264},{4},
		 {53,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88},{13,16,19,273},
		 {305,748,777,843},{187},{117},{584},{105},{506},{109,262},{270,494},{1},{},{},{20},{1,78},
		 {1},{1},{897},{},{566},{173,289,329,374,669},{},{16},{},{},{1},{220},{51},{},{22},
		 {115,142,143,163,239,313,318,378,670,770,771,772,773,874,1038},{10,38,84,106},{141},
		 {5,7,8},{58},{6,279,456},{652},{8,29,58,146,162,172,353,365,366,586,609,688,764,1092,1138,1378,1380,1543},
		 {},{1,6,7},{1,6,15},{1132},{1},{},{120,1035},{},{},{},{385,408,433,525,919},{},{},{1,8,27,46,122},{278},{},
		 {1215},{121},{319},{41},{10,39},{11,12,14}, {12,13,15,20,36,117,413,414},{418},{234},{},{286},{1,8,9},{41},{31},{38,39,40},{3,4,6,51,69,95,115,119},{67,113}};
		
		
		List<String> queryStrings = Files.readAllLines(Paths.get(getClass().getResource("/queries_short_ml.txt").toURI()));
		List<String> answerStrings = Files.readAllLines(Paths.get(getClass().getResource("/answers_ml.txt").toURI()));
		
		Corpus<List<String>> corpus = Configurations.builder()
				.srcDir(Paths.get("C://Users//mch//workdir//Experiments//Subject_systems//rhino"))
				.indexDir(Paths.get("C://Users//mch//workdir//Experiments//Subject_systems//rhino//indexMethodRecentAcir5.2.0"))
				.revision("5fa9e36")
				.granularity(Granularity.METHOD)
				.recentness(Recentness.RECENT)
				.source(Source.VCS)
				.build();
		
		Future<List<String>> future2 = executor.submit(corpus.create());
		
		while(!future2.isDone()){}
		
		List<List<Integer>> allPositions = new ArrayList<>();
		assertEquals(queryStrings.size(), 75);
		assertTrue(queryStrings.size() == answerStrings.size());
		for (int i = 0; i < queryStrings.size(); i++) {
			
			Set<String> answers = new HashSet<>(Arrays.asList(answerStrings.get(i).replace("\\s+", " ").trim().split(" ")));
			
			Future<Set<Component>> future = executor.submit(corpus.search(queryStrings.get(i)));

			Set<Component> components;
			try {
				components = future.get();
				List<Integer> positions = new ArrayList<>();

				for (Component c : components) {

					if (answers.contains(c.getPath())) {
						int position = c.getSearchPosition();
						assertTrue(position > 0);
						positions.add(position);
					}
				}
				allPositions.add(positions);
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		assertTrue(allPositions.size() > 0);
		assertEquals(Arrays.asList(truePositions), allPositions);
	}

}
