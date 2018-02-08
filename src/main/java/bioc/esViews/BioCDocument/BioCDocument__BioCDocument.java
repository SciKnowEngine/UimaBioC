package bioc.esViews.BioCDocument;

import bioc.esViews.BioCDocument.BioCDocument__BioCPassage;

import java.util.*;
import org.springframework.data.annotation.*;
import org.springframework.data.elasticsearch.annotations.*;
import static org.springframework.data.elasticsearch.annotations.FieldIndex.*;

import lombok.Data;

/**
 * Each {@code BioCDocument} in the {@link BioCCollection}. An id, typically from the original corpus, identifies the particular document. It includes {@link BioCPassage}s in the document and possibly {@link BioCRelation}s over annotations on the document.
*/
@Data
@Document(indexName = "biocdocument-index", type = "biocdocument", shards = 1, replicas = 0, refreshInterval = "-1")
public class BioCDocument__BioCDocument {
	/**
	 * Id to identify the particular {@code Document}.
	*/
	private String id;

	private Map<String, String>  infons;

	/**
	 * List of passages that comprise the document. For PubMed references, they might be "title" and "abstract". For full text papers, they might be Introduction, Methods, Results, and Conclusions. Or they might be paragraphs.
	*/
	@Field(type = FieldType.Nested)
	private List<BioCDocument__BioCPassage> passages = new ArrayList<BioCDocument__BioCPassage>();


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public BioCDocument__BioCDocument() {}
	public BioCDocument__BioCDocument(String id, Map<String, String>  infons, List<BioCDocument__BioCPassage> passages) {
		this.id = id;
		this.infons = infons;
		this.passages = passages;
	}


}
