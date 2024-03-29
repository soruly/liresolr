package net.semanticmetadata.lire.solr;

import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.update.CommitUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

public final class CacheUpdateRequestsProcessor extends UpdateRequestProcessor {

    private final SolrIndexSearcher searcher;

    private boolean wasCommitted = false;

    public CacheUpdateRequestsProcessor(SolrQueryRequest req, UpdateRequestProcessor next) {
        super(next);
        this.searcher = requireNonNull(req.getSearcher());
    }

    @Override
    public void processCommit(CommitUpdateCommand cmd) throws IOException {
        wasCommitted = true;
        super.processCommit(cmd);
    }

    @Override
    public void finish() throws IOException {
        super.finish();

        if (wasCommitted) {
            HashFrequenciesCache.updateAllCommit(searcher);
        }
    }

}
