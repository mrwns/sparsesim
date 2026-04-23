package org.retrosynthesis.chem.lucene;

import org.apache.lucene.search.similarities.ClassicSimilarity;

public class ChemicalSimilarity extends ClassicSimilarity {

	  @Override
	  public float idf(long docFreq, long numDocs) {
	//    return (float)(Math.log(numDocs/(double)(docFreq+1)) + 1.0);
	 return 1f;
	  }

	  @Override
	  public float tf(float freq) {
	
	//return (float) Math.sqrt(freq); //0.4588691816540429
		  return 1f; // 0.45956486533606883

	  }
	  


}
