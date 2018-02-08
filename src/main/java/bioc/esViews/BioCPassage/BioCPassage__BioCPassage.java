package bioc.esViews.BioCPassage;

import bioc.esViews.BioCPassage.BioCPassage__BioCDocument;
import bioc.esViews.BioCPassage.BioCPassage__BioCAnnotation;

import java.util.*;
import org.springframework.data.annotation.*;
import org.springframework.data.elasticsearch.annotations.*;
import static org.springframework.data.elasticsearch.annotations.FieldIndex.*;

import lombok.Data;

/**
 * One passage in a {@link Document}. This might be the {@code text} in the passage and possibly {@link Annotation}s over that text. It could be the {@link Sentence}s in the passage. In either case it might include {@link Relation}s over annotations on the passage.
*/
@Data
@Document(indexName = "biocpassage-index", type = "biocpassage", shards = 1, replicas = 0, refreshInterval = "-1")
public class BioCPassage__BioCPassage {
	private String id;

	/**
	 * The offset of the passage in the parent document. The significance of the exact value may depend on the source corpus. They should be sequential and identify the passage's position in the document. Since pubmed is extracted from an XML file, the title has an offset of zero, while the abstract is assumed to begin after the title and one space.
	*/
	private int offset;

	/**
	 * The original text of the passage.
	*/
	private String text;

	private Map<String, String>  infons;

	/**
	 * Annotations on the text of the passage.
	*/
	@Field(type = FieldType.Nested)
	private List<BioCPassage__BioCAnnotation> annotations = new ArrayList<BioCPassage__BioCAnnotation>();

	@Field(type = FieldType.Nested)
	private BioCPassage__BioCDocument document;


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public BioCPassage__BioCPassage() {}
	public BioCPassage__BioCPassage(String id, int offset, String text, Map<String, String>  infons, List<BioCPassage__BioCAnnotation> annotations, BioCPassage__BioCDocument document) {
		this.id = id;
		this.offset = offset;
		this.text = text;
		this.infons = infons;
		this.annotations = annotations;
		this.document = document;
	}


}
