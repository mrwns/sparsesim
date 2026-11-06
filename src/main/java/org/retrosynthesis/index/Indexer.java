package org.retrosynthesis.index;

import org.apache.lucene.document.*;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.apache.lucene.analysis.TokenStream;
import org.openscience.cdk.interfaces.IReaction;
import org.retrosynthesis.chem.FingerprintUtil;
import org.retrosynthesis.chem.IReactionFingerprinter;
import org.retrosynthesis.chem.ReactionFingerprinter;
import org.retrosynthesis.chem.SmilesXMol;

import static org.retrosynthesis.index.FieldNames.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

public class Indexer implements AutoCloseable {
    private final Directory dir;
    private final IndexWriter writer;
    private static final FieldType COUNT_FT;


    static {
        COUNT_FT = new FieldType();
        COUNT_FT.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        COUNT_FT.setStored(false);
        COUNT_FT.setTokenized(true);
        COUNT_FT.freeze();
    }

    public Indexer(Path indexDir) throws Exception {
        this.dir = FSDirectory.open(indexDir);
        this.writer = new IndexWriter(dir,
                new IndexWriterConfig(/* analyzer not used since we give TokenStream */));
    }

    public void addReaction(InputReaction reaction) throws Exception {
        addReaction(reaction.smiles(), reaction.id());
    }


    public void addReaction(String smiles, String id) throws Exception {
        try {
        
        var doc = new Document();
        
        doc.add(new StoredField(FIELD_RXN_SMILES, smiles));
        doc.add(new StringField(FIELD_RXN_ID, id, Store.YES));
        // doc.add(new StoredField(FIELD_FP_COUNTS, FingerprintUtil.countsToCsv(counts)));
        IReaction rxn = SmilesXMol.reaction(smiles);
        IReactionFingerprinter<Integer> fingerprinter = new ReactionFingerprinter<Integer>();
        Map<Integer, Integer> counts = fingerprinter.getRawFingerprint(rxn);
        
        
        doc.add(new NumericDocValuesField(FIELD_FP_L1, FingerprintUtil.l1(counts)));

        // Index features with frequencies via custom TokenStream
        TokenStream ts = new CountTokenStream(counts);
        doc.add(new Field(FIELD_RXNFP_ECFP, ts, COUNT_FT));

        writer.addDocument(doc);
        // System.out.println(smiles);      
      } catch (Exception e) {
             System.err.println(smiles + " error " + id);
             e.printStackTrace();
            //  throw new RuntimeException(e);
        }
    }

    public void addAll(Stream<InputReaction> reactions) throws Exception {
        reactions.forEach(r -> {
            try {
                addReaction(r);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        writer.commit();

    }

    @Override public void close() throws Exception { writer.close(); dir.close(); }
}
