package bioc.esViews.BioCAnnotation;

import bioc.esViews.BioCAnnotation.BioCAnnotation__BioCPassage;
import bioc.esViews.BioCAnnotation.BioCAnnotation__BioCLocation;

import java.util.*;

import lombok.Data;

/**
 * Stand off annotation. The connection to the original text can be made through the {@code location} and the {@code text} fields.
*/
@Data
public class BioCAnnotation__BioCAnnotation {
	/**
	 * Id used to identify this annotation in a {@link Relation}.
	*/
	private String id;

	/**
	 * The annotated text.
	*/
	private String text;

	private Map<String, String> infons;

	private List<BioCAnnotation__BioCLocation> locations = new ArrayList<BioCAnnotation__BioCLocation>();

	private BioCAnnotation__BioCPassage passage;


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public BioCAnnotation__BioCAnnotation() {}
	public BioCAnnotation__BioCAnnotation(String id, String text, Map<String, String> infons, List<BioCAnnotation__BioCLocation> locations, BioCAnnotation__BioCPassage passage) {
		this.id = id;
		this.text = text;
		this.infons = infons;
		this.locations = locations;
		this.passage = passage;
	}


}
