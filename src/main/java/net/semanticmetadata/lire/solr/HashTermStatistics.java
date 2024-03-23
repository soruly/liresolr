package net.semanticmetadata.lire.solr;

import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.search.SolrIndexSearcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Used to hold the hashing terms in memory.
 * Created by mlux on 08.12.2016.
 */
public class HashTermStatistics {

    private static final HashMap<String, Map<String, Integer>> TERM_STATS = new HashMap<>(8);


    public static void addToStatistics(SolrIndexSearcher searcher, String field) throws IOException {
        if (TERM_STATS.containsKey(field)) {
            return;
        }

        Terms terms = searcher.getSlowAtomicReader().terms(field);
        Map<String, Integer> term2docFreq = new HashMap<>(1000);
        TERM_STATS.put(field, term2docFreq);
        if (terms != null) {
            TermsEnum termsEnum = terms.iterator();
            BytesRef term;
            while ((term = termsEnum.next()) != null) {
                term2docFreq.put(term.utf8ToString(), termsEnum.docFreq());
            }
        }
    }

    public static int docFreq(String field, String term) {
        Integer termValue = TERM_STATS.get(field).get(term);
        return termValue != null ? termValue : 0;
    }

}
