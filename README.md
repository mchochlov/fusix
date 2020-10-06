## Natural language code search library

FUSIX is a natural language code search library directed at Feature Location, a result of research, designed to facilitate source code maintenance activities. To accomplish that, it combines information retrieval techniques with semantic information available in source code and version control systems. Some features of FUSIX:

Distinct Data Sources        | Coarse and Fine Granularity          | Natural Language Queries  
------------- |-------------| -----
Retrieve semantic information from source code and version control systems to enhance the search. Combine and filter these sources to achieve the best result.      | FUSIX allows to locate source code components of file and method-level granularity. | Locate interesting source code components using natural language, Google-like queries. 

## Context of research

Feature location is the task of finding the source code that implements specific functionality in software systems. A common approach is to leverage textual information in source code against a query, using Information Retrieval (IR) techniques. To address the paucity of meaningful terms in source code, alternative, relevant source-code descriptions, like change-sets could be leveraged. Hence, current research studies source code, lexical annotation by change-sets and characterizes the efficacy of this approach for IR-based feature location. A custom built tool, ACIR, was used to study different configurations of the approach and to compare it against a baseline approach.

The FUSIX library was created by Muslim Chochlov to support his PhD research and publications. It includes the ACIR, baseline approach, and BACIR, a combination of two aforementioned approaches.

The research is supervised by Jim Buckley and Michael English. 

## Getting started

### To create custom search corpus
```Java
ExecutorService executor = ... ;

Corpus<List<String>> corpus = Configurations.builder()
		.srcDir(Paths.get(...))
		.indexDir(Paths.get(...))
		.granularity(Granularity.FILE)
		.recentness(Recentness.RECENT)
		.filtered()
		.source(Source.VCS)
		.build();
		
Future<List<String>> future = executor.submit(corpus.create());
```

### To search corpus

```Java
String query = ... ;
Future<Set<Component>> future = executor.submit(corpus.search(query));

Set<Component> components = future.get();
components.forEach(System.out::println);
```

## Demo
You can try FUSIX library in action by using our [demo web application](https://intense-reef-67011.herokuapp.com/).
The corpus was prebuilt for 8 open source projects/their sub-modules: Rhino, Mylyn.Tasks, JGit, Jetty, Ant, Hudson, JMeter, Eclipse.Platform.Text.
You can use natural language queries to locate interesting source code components in these projects.

## Research group

1. Muslim Chochlov is a research fellow at Lero.

2. Jim Buckley is a senior lecturer in the Computer Science and Information Systems Department at the University of Limerick/Lero.

3. Michael English is a lecturer in the Computer Science and Information Systems Department at the University of Limerick, Ireland.

## Publications
When using this library in your work, please cite the following publications:

1. Chochlov, M., English, M., & Buckley, J. (2017). A historical, textual analysis approach to feature location. Information and Software Technology, 88, 110--126.

2. Chochlov, Muslim, Michael English, and Jim Buckley. "Using changeset descriptions as a data source to assist feature location." Source Code Analysis and Manipulation (SCAM), 2015 IEEE 15th International Working Conference on. IEEE, 2015.

## Acknowledgement
This research and library is a result of work that was supported, in part, by Science Foundation Ireland Grants 12/IP/1351 and 10/CE/I1855 to Lero the Irish Software Engineering Research Centre.
