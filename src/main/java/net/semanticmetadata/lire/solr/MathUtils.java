package net.semanticmetadata.lire.solr;

public final class MathUtils {

    public static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
