package bioc.esViews.BioCPassage;


import java.util.*;
import org.springframework.data.annotation.*;
import org.springframework.data.elasticsearch.annotations.*;
import static org.springframework.data.elasticsearch.annotations.FieldIndex.*;

import lombok.Data;

/**
 * Each {@code BioCDocument} in the {@link BioCCollection}. An id, typically from the original corpus, identifies the particular document. It includes {@link BioCPassage}s in the document and possibly {@link BioCRelation}s over annotations on the document.
*/
@Data
public class BioCPassage__BioCDocument {
	/**
	 * Id to identify the particular {@code Document}.
	*/
	private String id;

	private Map<String, String>  infons;


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public BioCPassage__BioCDocument() {}
	public BioCPassage__BioCDocument(String id, Map<String, String>  infons) {
		this.id = id;
		this.infons = infons;
	}


}
