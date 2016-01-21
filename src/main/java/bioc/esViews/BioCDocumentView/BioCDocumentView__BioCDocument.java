package bioc.esViews.BioCDocumentView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import bioc.BioCCollection;
import bioc.BioCRelation;
import lombok.Data;

/**
 * Each {@code BioCDocument} in the {@link BioCCollection}. An id, typically from the original corpus, identifies the particular document. It includes {@link BioCDocumentView__BioCPassage}s in the document and possibly {@link BioCRelation}s over annotations on the document.
*/
@Data
@Document(indexName = "biocdocument-index", type = "biocdocument", shards = 1, replicas = 0, refreshInterval = "-1")
public class BioCDocumentView__BioCDocument {
	@Id
	private String id;

	private Map<String,String> infons = new HashMap<String,String>();

	/**
	 * List of passages that comprise the document. For PubMed references, they might be "title" and "abstract". For full text papers, they might be Introduction, Methods, Results, and Conclusions. Or they might be paragraphs.
	*/
	@Field(type = FieldType.Nested)

	private List<BioCDocumentView__BioCPassage> passages = new ArrayList<BioCDocumentView__BioCPassage>();

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public BioCDocumentView__BioCDocument() {}
	public BioCDocumentView__BioCDocument(String id, Map<String,String> infons, List<BioCDocumentView__BioCPassage> passages) {
		this.id = id;
		this.infons = infons;
		this.passages = passages;
	}
	
	


}
