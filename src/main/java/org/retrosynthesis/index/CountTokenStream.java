package org.retrosynthesis.index;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

public final class CountTokenStream extends TokenStream {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final TermFrequencyAttribute freqAtt = addAttribute(TermFrequencyAttribute.class);
    
    private final Map<Integer, Integer> counts;
    private int currentIndex = 0;
    private final int[] hashes;
    
    public CountTokenStream(Map<Integer, Integer> counts) {
        this.counts = counts;
        this.hashes = counts.keySet().stream().mapToInt(Integer::intValue).toArray();
    }

    public CountTokenStream(BitSet counts) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int bit = counts.nextSetBit(0); bit >= 0; bit = counts.nextSetBit(bit + 1)) {
            map.put(bit, 1);
        }
        this.counts = map;
        this.hashes = map.keySet().stream().mapToInt(Integer::intValue).toArray();
    }

    
    
    @Override
    public boolean incrementToken() throws IOException {
        if (currentIndex >= hashes.length) {
            return false;
        }
        
        clearAttributes();
        
        int hash = hashes[currentIndex];
        int count = counts.get(hash);
        
        // Set the term (prefixed with 'f' to avoid conflicts with other terms)
        termAtt.setEmpty();
        termAtt.append("f" + Math.signum(count) + "_" + hash);
        
        // Set the frequency
        freqAtt.setTermFrequency(Math.abs(count));
        
        currentIndex++;
        return true;
    }
    
    @Override
    public void reset() throws IOException {
        super.reset();
        currentIndex = 0;
    }
}

