package net.semanticmetadata.lire.solr;

import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.search.SolrIndexSearcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

public final class HashFrequenciesCache {

    private static final HashMap<String, Map<String, Integer>> HASH_FREQUENCIES = new HashMap<>();

    public static int getHashFrequency(String field, String hash) {
        final var termStats = HASH_FREQUENCIES.get(field);
        return termStats != null
                ? termStats.getOrDefault(hash, 0)
                : 0;
    }

    public static void updateAllCommit(SolrIndexSearcher searcher) {
        for (String code : FeatureRegistry.getSupportedCodes()) {
            update(searcher, code + "_ha", true);
        }
    }

    public static void updateParameterField(SolrIndexSearcher searcher, String field) {
        update(searcher, field, false);
    }

    private static void update(SolrIndexSearcher searcher, String field, boolean wasCommit) {
        if (!wasCommit && HASH_FREQUENCIES.containsKey(field)) {
            return;
        }

        var termCounts = getTermCounts(searcher, field);
        HASH_FREQUENCIES.put(field, termCounts);
    }

    private static Map<String, Integer> getTermCounts(SolrIndexSearcher searcher, String field) {
        try {
            Terms terms = searcher.getSlowAtomicReader().terms(field);

            if (terms == null) {
                return emptyMap();
            }

            final var termCounts = new HashMap<String, Integer>(4096);
            TermsEnum termsEnum = terms.iterator();
            BytesRef term;

            while ((term = termsEnum.next()) != null) {
                termCounts.put(term.utf8ToString(), termsEnum.docFreq());
            }

            return termCounts;
        } catch (IOException e) {
            System.err.println(e);
            System.err.println("Failed to load hash terms for field: " + field);
        }

        return emptyMap();
    }

}
