package bioc.esViews.BioCDocumentView;

import java.util.*;
import org.springframework.data.annotation.*;
import org.springframework.data.elasticsearch.annotations.*;

import bioc.esViews.BioCDocumentView.BioCDocumentView__BioCAnnotation;

import static org.springframework.data.elasticsearch.annotations.FieldIndex.*;

import lombok.Data;
/**
 * One passage in a {@link Document}. This might be the {@code text} in the passage and possibly {@link Annotation}s over that text. It could be the {@link Sentence}s in the passage. In either case it might include {@link Relation}s over annotations on the passage.
*/
@Data
public class BioCDocumentView__BioCPassage {

	/**
	 * The offset of the passage in the parent document. The significance of the exact value may depend on the source corpus. They should be sequential and identify the passage's position in the document. Since pubmed is extracted from an XML file, the title has an offset of zero, while the abstract is assumed to begin after the title and one space.
	*/
	private int offset;

	/**
	 * The original text of the passage.
	*/
	private String text;

	/**
	 * Information of text in the passage. For PubMed references, it might be "title" or "abstract". For full text papers, it might be Introduction, Methods, Results, or Conclusions. Or they might be paragraphs.
	*/
	private Map<String, String> infons = new HashMap<String,String>();

	/**
	 * Annotations on the text of the passage.
	*/
	@Field(type = FieldType.Nested)
	private List<BioCDocumentView__BioCAnnotation> annotations = new ArrayList<BioCDocumentView__BioCAnnotation>();


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public BioCDocumentView__BioCPassage() {}
	public BioCDocumentView__BioCPassage(int offset, String text, Map<String,String> infons, List<BioCDocumentView__BioCAnnotation> annotations) {
		this.offset = offset;
		this.text = text;
		this.infons = infons;
		this.annotations = annotations;
	}

}
