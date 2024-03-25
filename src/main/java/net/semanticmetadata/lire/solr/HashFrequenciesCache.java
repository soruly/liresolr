package net.semanticmetadata.lire.solr;

import java.util.HashMap;
import java.util.Map;


public final class HashFrequenciesCache {

    private static final HashMap<String, Map<Integer, Integer>> HASH_FREQUENCIES = new HashMap<>(8);

    public static int getHashFrequency(String field, int value) {
        final var termStats = HASH_FREQUENCIES.get(field);
        return termStats != null
                ? termStats.getOrDefault(value, 0)
                : 0;
    }

}
