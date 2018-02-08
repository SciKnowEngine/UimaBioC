package bioc.esViews.BioCDocument;

import bioc.esViews.BioCDocument.BioCDocument__BioCAnnotation;

import java.util.*;
import org.springframework.data.annotation.*;
import org.springframework.data.elasticsearch.annotations.*;
import static org.springframework.data.elasticsearch.annotations.FieldIndex.*;

import lombok.Data;

/**
 * One passage in a {@link Document}. This might be the {@code text} in the passage and possibly {@link Annotation}s over that text. It could be the {@link Sentence}s in the passage. In either case it might include {@link Relation}s over annotations on the passage.
*/
@Data
public class BioCDocument__BioCPassage {
	private String id;

	/**
	 * The offset of the passage in the parent document. The significance of the exact value may depend on the source corpus. They should be sequential and identify the passage's position in the document. Since pubmed is extracted from an XML file, the title has an offset of zero, while the abstract is assumed to begin after the title and one space.
	*/
	private int offset;

	/**
	 * The original text of the passage.
	*/
	private String text;

	private Map<String, String> infons;

	/**
	 * Annotations on the text of the passage.
	*/
	@Field(type = FieldType.Nested)
	private List<BioCDocument__BioCAnnotation> annotations = new ArrayList<BioCDocument__BioCAnnotation>();


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public BioCDocument__BioCPassage() {}
	public BioCDocument__BioCPassage(String id, int offset, String text, Map<String, String>  infons, List<BioCDocument__BioCAnnotation> annotations) {
		this.id = id;
		this.offset = offset;
		this.text = text;
		this.infons = infons;
		this.annotations = annotations;
	}


}
