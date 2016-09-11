package co.fusix.component;

public abstract class Component {

	protected static final String EMPTY = "";
	
	public enum Fields{PATH, CONTENT}

	public abstract String getPath();
	
	public abstract String getFilePath();
	
	public abstract String getContent();
	
	public abstract void addContent(String content);
	
	public abstract int getStartLine();
	public abstract int getEndLine();
	
}
