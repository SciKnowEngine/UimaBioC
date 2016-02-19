package edu.isi.bmkeg.uimaBioC.hadoop;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.TypeCapability;

import com.ibm.icu.text.CharsetDetector;

import de.tudarmstadt.ukp.dkpro.core.api.io.ResourceCollectionReaderBase;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;

/**
 * UIMA collection reader for plain text files.
 *
 * @author Richard Eckart de Castilho
 */
@TypeCapability(
        outputs={
                "de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData"})

public class TextReader
	extends ResourceCollectionReaderBase
{
	/**
	 * Automatically detect encoding.
	 *
	 * @see CharsetDetector
	 */
	public static final String ENCODING_AUTO = "auto";

	/**
	 * Name of configuration parameter that contains the character encoding used by the input files.
	 */
	public static final String PARAM_ENCODING = ComponentParameters.PARAM_SOURCE_ENCODING;
	@ConfigurationParameter(name = PARAM_ENCODING, mandatory = true, defaultValue = "UTF-8")
	private String encoding;

	@Override
	public void getNext(CAS aCAS)
		throws IOException, CollectionException
	{
		Resource res = nextFile();
		initCas(aCAS, res);

		InputStream is = null;
		try {
			is = new BufferedInputStream(res.getInputStream());
			if (ENCODING_AUTO.equals(encoding)) {
				CharsetDetector detector = new CharsetDetector();
				aCAS.setDocumentText(IOUtils.toString(detector.getReader(is, null)));
			}
			else {
				aCAS.setDocumentText(IOUtils.toString(is, encoding));
			}
		}
		finally {
			closeQuietly(is);
		}
	}
}
