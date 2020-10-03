package co.fusix.component;


public final class GenericComponent extends Component {

	
	private final String path;
	private final int startLine;
	private final int endLine;
	private String content;
	private int searchPosition;

	
	public int getSearchPosition() {
		return searchPosition;
	}

	public void setSearchPosition(int searchPosition) {
		this.searchPosition = searchPosition;
	}

	public GenericComponent(String path) {
		this(path, EMPTY);
	}
	
	public GenericComponent(String filePath, String content) {
		this(filePath, content, -1, -1);
	}
	
	public GenericComponent(String filePath, int startLine, int endLine) {
		this(filePath, EMPTY, startLine, endLine);
	}
	
	public GenericComponent(String filePath, String content, int startLine, int endLine) {
		this.path = filePath;
		this.content = content;
		this.startLine = startLine;
		this.endLine = endLine;
	}
	@Override
	public int getStartLine() {
		return startLine;
	}

	@Override
	public int getEndLine() {
		return endLine;
	}

	@Override
	public String getPath() {return this.path;}

	@Override
	public String getFilePath() {
		return this.path.split("::")[0].replace("\\", "/");
	}
	
	public String getContent() {return this.content;}
	
	@Override
	public void addContent(String content){
		this.content = this.content + content;
	}

	@Override
	public String toString() {
		return "GenericComponent: " + searchPosition + " [path=" + path + "]";
	}

}
