package bioc.esViews.BioCDocumentView;

import java.util.*;
import org.springframework.data.annotation.*;
import org.springframework.data.elasticsearch.annotations.*;

import bioc.esViews.BioCDocumentView.BioCDocumentView__BioCLocation;

import static org.springframework.data.elasticsearch.annotations.FieldIndex.*;

import lombok.Data;
/**
 * Stand off annotation. The connection to the original text can be made through the {@code location} and the {@code text} fields.
*/
@Data
public class BioCDocumentView__BioCAnnotation {
	@Id
	private String id;

	private Map<String,String> infons = new HashMap<String,String>();

	/**
	 * The annotated text.
	*/
	private String text;

	@Field(type = FieldType.Nested)
	private List<BioCDocumentView__BioCLocation> locations = new ArrayList<BioCDocumentView__BioCLocation>();


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public BioCDocumentView__BioCAnnotation() {}
	public BioCDocumentView__BioCAnnotation(String id, Map<String,String> infons, String text, List<BioCDocumentView__BioCLocation> locations) {
		this.id = id;
		this.infons = infons;
		this.text = text;
		this.locations = locations;
	}


}
