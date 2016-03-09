package edu.isi.bmkeg.uimaBioC.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;

public class IdfMap {
    private Multiset<String> documentFreqMap;

    private int totalDocumentCount;

    public IdfMap() {
      this.documentFreqMap = LinkedHashMultiset.create();
      this.totalDocumentCount = 0;
    }

    public void add(String term) {
      this.documentFreqMap.add(term);
    }

    public void incTotalDocumentCount() {
      this.totalDocumentCount++;
    }

    public int getTotalDocumentCount() {
      return this.totalDocumentCount;
    }

    public int getDF(String term) {
      return this.documentFreqMap.count(term);
    }

    public double getIDF(String term) {
      int df = this.getDF(term);

      // see issue 396 for discussion about the ratio here:
      // https://code.google.com/p/cleartk/issues/detail?id=396
      // In short, we add 1 to the document frequency for smoothing purposes so that unseen words
      // will not generate a NaN. We add 2 to the numerator to make sure that we get a positive
      // value for words that appear in every document (i.e. when df == totalDocumentCount)
      return Math.log((this.totalDocumentCount + 2) / (double) (df + 1));
    }

    public void save(URI outputURI) throws IOException {
      File out = new File(outputURI);
      BufferedWriter writer = null;
      writer = new BufferedWriter(new FileWriter(out));
      writer.append(String.format(Locale.ROOT, "NUM DOCUMENTS\t%d\n", this.totalDocumentCount));
      for (Multiset.Entry<String> entry : this.documentFreqMap.entrySet()) {
        writer.append(String.format(Locale.ROOT, "%s\t%d\n", entry.getElement(), entry.getCount()));
      }
      writer.close();
    }

    public void load(URI inputURI) throws IOException {
      File in = new File(inputURI);
      BufferedReader reader = null;
      // this.documentFreqMap = LinkedHashMultiset.create();
      reader = new BufferedReader(new FileReader(in));
      // First line specifies the number of documents
      String firstLine = reader.readLine();
      String[] keyValuePair = firstLine.split("\\t");
      this.totalDocumentCount = Integer.parseInt(keyValuePair[1]);

      // The rest of the lines are the term counts
      String line = null;
      while ((line = reader.readLine()) != null) {
        String[] termFreqPair = line.split("\\t");
        this.documentFreqMap.add(termFreqPair[0], Integer.parseInt(termFreqPair[1]));
      }
      reader.close();
    }
    
  }


