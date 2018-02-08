package bioc.esViews.BioCPassage;

import bioc.esViews.BioCPassage.BioCPassage__BioCLocation;

import java.util.*;
import org.springframework.data.annotation.*;
import org.springframework.data.elasticsearch.annotations.*;
import static org.springframework.data.elasticsearch.annotations.FieldIndex.*;

import lombok.Data;

/**
 * Stand off annotation. The connection to the original text can be made through the {@code location} and the {@code text} fields.
*/
@Data
public class BioCPassage__BioCAnnotation {
	/**
	 * Id used to identify this annotation in a {@link Relation}.
	*/
	private String id;

	/**
	 * The annotated text.
	*/
	private String text;

	private Map<String, String>  infons;

	@Field(type = FieldType.Nested)
	private List<BioCPassage__BioCLocation> locations = new ArrayList<BioCPassage__BioCLocation>();


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public BioCPassage__BioCAnnotation() {}
	public BioCPassage__BioCAnnotation(String id, String text, Map<String, String>  infons, List<BioCPassage__BioCLocation> locations) {
		this.id = id;
		this.text = text;
		this.infons = infons;
		this.locations = locations;
	}


}
