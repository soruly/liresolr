package net.semanticmetadata.lire.solr;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;

public class SearchParameters {

    private static final int DEFAULT_NUMBER_OF_RESULTS = 60;
    private static final double DEFAULT_NUMBER_OF_QUERY_TERMS = 0.33;
    private static final int DEFAULT_NUMBER_OF_CANDIDATES = 10000;
    private static final boolean DEFAULT_USE_METRIC_SPACES = false;



    public final String id;
    public final String url;
    public final String field;
    public final int rows;
    public final double numberOfQueryTerms;
    public final int numberOfCandidateResults;
    public final boolean useMetricSpaces;

    public SearchParameters(SolrQueryRequest request) {
        final SolrParams params = request.getParams();
        this.id = params.get("id");
        this.url = params.get("url");
        this.field = parseField(params);
        this.rows = params.getInt("rows", DEFAULT_NUMBER_OF_RESULTS);
        this.numberOfQueryTerms = params.getDouble("accuracy", DEFAULT_NUMBER_OF_QUERY_TERMS);
        this.numberOfCandidateResults = params.getInt("candidates", DEFAULT_NUMBER_OF_CANDIDATES);
        this.useMetricSpaces = params.getBool("ms", DEFAULT_USE_METRIC_SPACES);
    }

    private static String parseField(SolrParams params) {
        String field = params.get("field", "cl_ha");

        if (!field.endsWith("_ha")) {
            field += "_ha";
        }

        return field;
    }
}
