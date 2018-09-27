package bioc.esViews.BioCDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * Stand off annotation. The connection to the original text can be made through the {@code location} and the {@code text} fields.
*/
@Data
public class BioCDocument__BioCAnnotation {
	/**
	 * Id used to identify this annotation in a {@link Relation}.
	*/
	private String id;

	/**
	 * The annotated text.
	*/
	private String text;

	private Map<String, String>  infons;

	private List<BioCDocument__BioCLocation> locations = new ArrayList<BioCDocument__BioCLocation>();


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public BioCDocument__BioCAnnotation() {}
	public BioCDocument__BioCAnnotation(String id, String text, Map<String, String>  infons, List<BioCDocument__BioCLocation> locations) {
		this.id = id;
		this.text = text;
		this.infons = infons;
		this.locations = locations;
	}


}
