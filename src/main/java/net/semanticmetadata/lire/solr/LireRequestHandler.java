/*
 * This file is part of the LIRE project: http://www.semanticmetadata.net/lire
 * LIRE is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * LIRE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LIRE; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * We kindly ask you to refer the any or one of the following publications in
 * any publication mentioning or employing Lire:
 *
 * Lux Mathias, Savvas A. Chatzichristofis. Lire: Lucene Image Retrieval -
 * An Extensible Java CBIR Library. In proceedings of the 16th ACM International
 * Conference on Multimedia, pp. 1085-1088, Vancouver, Canada, 2008
 * URL: http://doi.acm.org/10.1145/1459359.1459577
 *
 * Lux Mathias. Content Based Image Retrieval with LIRE. In proceedings of the
 * 19th ACM International Conference on Multimedia, pp. 735-738, Scottsdale,
 * Arizona, USA, 2011
 * URL: http://dl.acm.org/citation.cfm?id=2072432
 *
 * Mathias Lux, Oge Marques. Visual Information Retrieval using Java and LIRE
 * Morgan & Claypool, 2013
 * URL: http://www.morganclaypool.com/doi/abs/10.2200/S00468ED1V01Y201301ICR025
 *
 * Copyright statement:
 * --------------------
 * (c) 2002-2013 by Mathias Lux (mathias@juggle.at)
 *     http://www.semanticmetadata.net/lire, http://www.lire-project.net
 */

package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.features.GlobalFeature;
import net.semanticmetadata.lire.imageanalysis.features.global.ColorLayout;
import net.semanticmetadata.lire.indexers.hashing.BitSampling;
import net.semanticmetadata.lire.indexers.hashing.MetricSpaces;
import net.semanticmetadata.lire.solr.features.ShortFeatureCosineDistance;
import net.semanticmetadata.lire.solr.tools.EncodeAndHashCSV;
import net.semanticmetadata.lire.solr.tools.RandomAccessBinaryDocValues;
import net.semanticmetadata.lire.solr.tools.Utilities;
import net.semanticmetadata.lire.utils.StatsUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocList;
import org.apache.solr.search.QParser;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.SyntaxError;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static net.semanticmetadata.lire.solr.MathUtils.clampInt;

/**
 * This is the main LIRE RequestHandler for the Solr Plugin. It supports query by example using the indexed id,
 * an url or a feature vector. Furthermore, feature extraction and random selection of images are supported.
 *
 * @author Mathias Lux, mathias@juggle.at, 07.07.13
 */

public class LireRequestHandler extends RequestHandlerBase {

    /**
     * number of candidate results retrieved from the index. The higher this number, the slower,
     * the but more accurate the retrieval will be. 10k is a good value for starters.
     */
    private int numberOfCandidateResults = 10000;


    /**
     * The number of query terms that go along with the TermsFilter search. We need some to get a
     * score, the less the faster. I put down a minimum of three in the method, this value gives
     * the percentage of the overall number used (selected randomly).
     */
    private double numberOfQueryTerms = 0.33;


    /**
     * If metric spaces should be used instead of BitSampling.
     */
    private boolean useMetricSpaces = false;


    static {
        HashingMetricSpacesManager.init(); // load reference points from disk.
    }

    @Override
    public void init(NamedList args) {
        super.init(args);
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        if (req.getParams().get("id") != null) {
            handleIdSearch(req, rsp);
        } else if (req.getParams().get("extract") != null) {
            handleExtract(req, rsp);
        } else {
            handleUploadSearch(req, rsp);
        }
    }

    /**
     * Handles the get parameters id, field and rows.
     */
    private void handleIdSearch(SolrQueryRequest req, SolrQueryResponse rsp) {
        SearchParameters parameters = new SearchParameters(req);
        SolrIndexSearcher searcher = req.getSearcher();
        try {
            int queryDocId = searcher.getFirstMatch(new Term("id", parameters.id));

            numberOfQueryTerms = parameters.numberOfQueryTerms;
            numberOfCandidateResults = parameters.numberOfCandidateResults;
            useMetricSpaces = parameters.useMetricSpaces;

            GlobalFeature queryFeature = (GlobalFeature) FeatureRegistry.getClassForHashField(parameters.field).newInstance();
            rsp.add("QueryField", parameters.field);
            rsp.add("QueryFeature", queryFeature.getClass().getName());
            if (queryDocId > -1) {

                BinaryDocValues binaryValues = new RandomAccessBinaryDocValues(() -> {
                    try {
                        return MultiDocValues.getBinaryValues(searcher.getIndexReader(), FeatureRegistry.getFeatureFieldName(parameters.field));
                    } catch (IOException e) {
                        throw new RuntimeException("BinaryDocValues problem.", e);
                    }

                });
                if (binaryValues == null) {
                    rsp.add("Error", "Could not find the DocValues of the query document. Are they in the index? Id: " + parameters.id);
                }

                BytesRef bvBytesRef = getBytesRef(binaryValues, queryDocId);
                queryFeature.setByteArrayRepresentation(bvBytesRef.bytes, bvBytesRef.offset, bvBytesRef.length);

                Query query = null;
                if (numberOfQueryTerms >= 0.90) {
                    query = new MatchAllDocsQuery();
                    rsp.add("Note", "Switching to AllDocumentsQuery because accuracy is set higher than 0.9.");
                } else {
                    query = getQuery(req.getCore().getName(), parameters.field, queryFeature, rsp);
                }
                doSearch(req, rsp, searcher, parameters.field, parameters.rows, getFilterQueries(req), query, queryFeature);
            } else {
                rsp.add("Error", "Did not find an image with the given id " + parameters.id);
            }
        } catch (Exception e) {
            rsp.add(
                    "Error",
                    "There was an error with your search for the image with the id " + parameters.id + ": " + e.getMessage()
            );
        }
    }

    /**
     * Parses the fq param and adds it as a list of filter queries or reverts to null if nothing is found
     * or an Exception is thrown.
     *
     * @param req
     * @return either a query from the QueryParser or null
     */
    private List<Query> getFilterQueries(SolrQueryRequest req) {
        List<Query> filters = null;

        String[] fqs = req.getParams().getParams("fq");
        if (fqs != null && fqs.length != 0) {
            filters = new ArrayList<>(fqs.length);
            try {
                for (String fq : fqs) {
                    if (fq != null && !fq.trim().isEmpty()) {
                        QParser fqp = QParser.getParser(fq, req);
                        fqp.setIsFilter(true);
                        filters.add(fqp.getQuery());
                    }
                }
            } catch (SyntaxError e) {
                e.printStackTrace();
            }

            if (filters.isEmpty()) {
                filters = null;
            }
        }
        return filters;
    }

    /**
     * Searches for an image given by HTTP POST. Note that (i) extracting image features takes time and
     * (ii) not every image is readable by Java.
     */
    private void handleUploadSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, InstantiationException, IllegalAccessException {
        SearchParameters parameters = new SearchParameters(req);
        numberOfQueryTerms = parameters.numberOfQueryTerms;
        numberOfCandidateResults = parameters.numberOfCandidateResults;
        useMetricSpaces = parameters.useMetricSpaces;

        System.out.println("Upload search: " + parameters);

        GlobalFeature feat = null;
        Query query = null;
        // wrapping the whole part in the try
        try {
            ImageIO.setUseCache(false);
            BufferedImage img = readImageFromStream(req, rsp);
            feat = extractImageFeatures(parameters.field, img);
            query = getQuery(req.getCore().getName(), parameters.field, feat, rsp);

        } catch (Exception e) {
            rsp.add("Error", "Error reading image from upload: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Search failed: " + parameters);
        }
        // search if the feature has been extracted and query is there.
        if (feat != null && query != null) {
            doSearch(req, rsp, req.getSearcher(), parameters.field, parameters.rows, getFilterQueries(req), query, feat);
        }
    }

    /**
     * Methods orders around the hashes already by docFreq removing those with docFreq == 0
     */
    private void handleExtract(SolrQueryRequest req, SolrQueryResponse rsp) {
        SearchParameters parameters = new SearchParameters(req);

        SolrParams params = req.getParams();
        String paramUrl = params.get("extract");

        useMetricSpaces = parameters.useMetricSpaces;
        double accuracy = parameters.numberOfQueryTerms;
        GlobalFeature feat;

        try {
            if (!parameters.field.startsWith("sf")) {
                ImageIO.setUseCache(false);
                BufferedImage img = ImageIO.read(new URL(paramUrl).openStream());
                feat = extractImageFeatures(parameters.field, img);
            } else {
                // we assume that this is a generic short feature, like it is used in context of deep features.
                feat = new ShortFeatureCosineDistance();
                String[] featureDoublesAsStrings = paramUrl.split(",");
                double[] featureDoubles = new double[featureDoublesAsStrings.length];
                for (int i = 0; i < featureDoubles.length; i++) {
                    featureDoubles[i] = Double.parseDouble(featureDoublesAsStrings[i]);
                }
                featureDoubles = Utilities.toCutOffArray(featureDoubles, EncodeAndHashCSV.TOP_N_CLASSES); // max norm
                short[] featureShort = Utilities.toShortArray(featureDoubles); // quantize
                ((ShortFeatureCosineDistance) feat).setData(featureShort);
            }

            rsp.add("histogram", Base64.encodeBase64String(feat.getByteArrayRepresentation()));

            int[] hashes = BitSampling.generateHashes(feat.getFeatureVector());

            String hashStrings = Arrays.stream(hashes)
                    .boxed()
                    .map(hash -> Integer.toString(hash))
                    .collect(Collectors.joining(","));

            String queryableHashes = computeQueryableHashes(parameters.field, hashes, accuracy)
                    .stream()
                    .map(hash -> Integer.toString(hash))
                    .collect(Collectors.joining(","));

            rsp.add("bs_list", hashStrings);
            rsp.add("bs_query", queryableHashes);

            if (MetricSpaces.supportsFeature(feat)) {
                rsp.add("ms_list", MetricSpaces.generateHashList(feat));
                int queryLength = (int) StatsUtils.clamp(
                        accuracy * MetricSpaces.getPostingListLength(feat), 3, MetricSpaces.getPostingListLength(feat)
                );
                rsp.add("ms_query", MetricSpaces.generateBoostedQuery(feat, queryLength));
            }
        } catch (Exception e) {
            rsp.add("Error", "Error reading image from URL: " + paramUrl + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Actual search implementation based on (i) hash based retrieval and (ii) feature based re-ranking.
     *
     * @param req           the SolrQueryRequest
     * @param rsp           the response to write the data to
     * @param searcher      the actual index searcher object to search the index
     * @param hashFieldName the name of the field the hashes can be found
     * @param maximumHits   the maximum number of hits, the smaller the faster
     * @param filterQueries can be null
     * @param query         the (Boolean) query for querying the candidates from the IndexSearcher
     * @param queryFeature  the image feature used for re-ranking the results
     */
    private void doSearch(SolrQueryRequest req, SolrQueryResponse rsp, SolrIndexSearcher searcher, String hashFieldName,
                          int maximumHits, List<Query> filterQueries, Query query, GlobalFeature queryFeature)
            throws IOException, IllegalAccessException, InstantiationException {
        GlobalFeature tmpFeature = queryFeature.getClass().newInstance();
        long time = System.currentTimeMillis();

        String featureFieldName = FeatureRegistry.getFeatureFieldName(hashFieldName);

        BinaryDocValues binaryValues = new RandomAccessBinaryDocValues(() -> {
            try {
                return MultiDocValues.getBinaryValues(searcher.getIndexReader(), featureFieldName);
            } catch (IOException e) {
                throw new RuntimeException("BinaryDocValues problem.", e);
            }
        });

        time = System.currentTimeMillis() - time;
        rsp.add("DocValuesOpenTime", time + "");

        Iterator<Integer> docIterator;
        long numberOfResults = 0;
        time = System.currentTimeMillis();
        if (filterQueries != null) {
            DocList docList = searcher.getDocList(query, filterQueries, Sort.RELEVANCE, 0, numberOfCandidateResults, 0);
            numberOfResults = docList.size();
            docIterator = docList.iterator();
        } else {
            TopDocs docs = searcher.search(query, numberOfCandidateResults);
            numberOfResults = docs.totalHits.value;
            docIterator = new TopDocsIterator(docs);
        }
        time = System.currentTimeMillis() - time;
        rsp.add("RawDocsCount", numberOfResults + "");
        rsp.add("RawDocsSearchTime", time + "");
        time = System.currentTimeMillis();
        TreeSet<CachingSimpleResult> resultScoreDocs = getReRankedResults(
                docIterator, binaryValues, queryFeature, tmpFeature,
                maximumHits, searcher);

        time = System.currentTimeMillis() - time;
        rsp.add("ReRankSearchTime", time + "");

        SolrDocumentList results = getResults(req, resultScoreDocs);
        rsp.add("response", results);
    }

    private static SolrDocumentList getResults(SolrQueryRequest req, TreeSet<CachingSimpleResult> resultScoreDocs) {
        SolrDocumentList list = new SolrDocumentList();
        for (CachingSimpleResult result : resultScoreDocs) {
            list.add(mapResultToDocument(req, result));
        }
        return list;
    }

    private static SolrDocument mapResultToDocument(SolrQueryRequest req, CachingSimpleResult result) {
        Map<String, Object> m = new HashMap<>();
        String fieldsRequested = req.getParams().get("fl");

        m.put("d", result.getDistance());

        if (fieldsRequested == null) {
            m.put("id", result.getDocument().get("id"));
            if (result.getDocument().get("title") != null) {
                m.put("title", result.getDocument().get("title"));
            }
        } else {
            if (fieldsRequested.contains("score")) {
                m.put("score", result.getDistance());
            }
            if (fieldsRequested.contains("*")) {
                // all fields
                for (IndexableField field : result.getDocument().getFields()) {
                    String tmpField = field.name();
                    appendField(result, tmpField, m);
                }
            } else {
                boolean splitOnComma = fieldsRequested.contains(",");
                String delimiter = splitOnComma ? "," : "";
                StringTokenizer st = new StringTokenizer(fieldsRequested, delimiter);

                while (st.hasMoreElements()) {
                    String tmpField = st.nextToken();
                    appendField(result, tmpField, m);
                }
            }
        }

        return new SolrDocument(m);
    }

    private static void appendField(CachingSimpleResult result, String tmpField, Map<String, Object> resultFields) {
        IndexableField[] fields = result.getDocument().getFields(tmpField);

        if (fields.length == 0) {
            return;
        }

        Object value = fields.length > 1
                ? result.getDocument().getValues(tmpField)
                : fields[0].stringValue();

        resultFields.put(fields[0].name(), value);
    }

    private TreeSet<CachingSimpleResult> getReRankedResults(
            Iterator<Integer> docIterator, BinaryDocValues binaryValues,
            GlobalFeature queryFeature, GlobalFeature tmpFeature,
            int maximumHits, IndexSearcher searcher) throws IOException {

        TreeSet<CachingSimpleResult> resultScoreDocs = new TreeSet<>();
        double maxDistance = -1f;
        double tmpScore;
        BytesRef bytesRef;
        CachingSimpleResult tmpResult;
        while (docIterator.hasNext()) {
            // using DocValues to retrieve the field values ...
            int doc = docIterator.next();

            bytesRef = getBytesRef(binaryValues, doc);
            tmpFeature.setByteArrayRepresentation(bytesRef.bytes, bytesRef.offset, bytesRef.length);
            // Getting the document from the index.
            // This is the slow step based on the field compression of stored fields.
            tmpScore = queryFeature.getDistance(tmpFeature);
            if (resultScoreDocs.size() < maximumHits) {
                resultScoreDocs.add(new CachingSimpleResult(tmpScore, searcher.doc(doc), doc));
                maxDistance = resultScoreDocs.last().getDistance();
            } else if (tmpScore < maxDistance) {
                // if it is nearer to the sample than at least one of the current set:
                // remove the last one ...
                tmpResult = resultScoreDocs.last();
                resultScoreDocs.remove(tmpResult);
                // set it with new values and re-insert.
                tmpResult.set(tmpScore, searcher.doc(doc), doc);
                resultScoreDocs.add(tmpResult);
                // and set our new distance border ...
                maxDistance = resultScoreDocs.last().getDistance();
            }
        }
        return resultScoreDocs;
    }

    @Override
    public String getDescription() {
        return "LIRE Request Handler to add images to an index and search them. Search images by id, by url and by extracted features.";
    }

    /**
     * Makes a Boolean query out of a list of hashes by ordering them ascending using their docFreq and
     * then only using the most distinctive ones, defined by sizePercentage in [0, 1], sizePercentage=1 takes all.
     *
     * @param hashes
     * @param paramField
     * @param sizePercentage in [0, 1]
     * @return
     */
    private BooleanQuery createQuery(int[] hashes, String paramField, double sizePercentage) {
        Collection<Integer> queryableHashes = computeQueryableHashes(paramField, hashes, sizePercentage);
        Query hashesQuery = IntPoint.newSetQuery(paramField, queryableHashes);

        return new BooleanQuery.Builder()
                .add(hashesQuery, BooleanClause.Occur.SHOULD)
                .build();
    }

    private Collection<Integer> computeQueryableHashes(String paramField, int[] hashes, double sizePercentage) {
        int hashesCount = clampInt((int) Math.floor(hashes.length * sizePercentage), 1, hashes.length);
        return Arrays.stream(hashes)
                .boxed()
                .filter(hash -> HashFrequenciesCache.getHashFrequency(paramField, hash) > 0)
                .sorted(Comparator.comparingInt(hash -> HashFrequenciesCache.getHashFrequency(paramField, hash)))
                .limit(hashesCount)
                .collect(toList());
    }

    private BytesRef getBytesRef(BinaryDocValues bdv, int docId) throws IOException {
        if (bdv != null && bdv.advance(docId) == docId) {
            return bdv.binaryValue();
        }
        return new BytesRef(BytesRef.EMPTY_BYTES);
    }

    private GlobalFeature extractImageFeatures(String paramField, BufferedImage img) throws InstantiationException, IllegalAccessException {
        GlobalFeature feat;

        if (FeatureRegistry.getClassForHashField(paramField) == null) {
            feat = new ColorLayout();
        } else {
            feat = (GlobalFeature) FeatureRegistry.getClassForHashField(paramField).newInstance();
        }

        feat.extract(img);
        return feat;
    }

    private Query getQuery(String coreName, String paramField, GlobalFeature feat, SolrQueryResponse rsp) throws ParseException {
        if (!useMetricSpaces) {
            HashFrequenciesCache.updateAll(coreName); // FIXME Remove and run in a spare thread.
            int[] hashes = BitSampling.generateHashes(feat.getFeatureVector());
            return createQuery(hashes, paramField, numberOfQueryTerms);
        }

        if (MetricSpaces.supportsFeature(feat)) {
            int queryLength = (int) StatsUtils.clamp(numberOfQueryTerms * MetricSpaces.getPostingListLength(feat), 3, MetricSpaces.getPostingListLength(feat));
            String msQuery = MetricSpaces.generateBoostedQuery(feat, queryLength);
            QueryParser qp = new QueryParser(paramField.replace("_ha", "_ms"), new WhitespaceAnalyzer());
            return qp.parse(msQuery);
        }

        rsp.add("Error", "Feature not supported by MetricSpaces: " + feat.getClass().getSimpleName());
        return new MatchAllDocsQuery();
    }

    private static BufferedImage readImageFromStream(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException {
        InputStream stream = null;
        Iterable<ContentStream> streams = req.getContentStreams();

        if (streams != null) {
            Iterator<ContentStream> iter = streams.iterator();
            if (iter.hasNext()) {
                stream = iter.next().getStream();
            }
            if (iter.hasNext()) {
                rsp.add("Error", "Does not support multiple ContentStreams");
            }
        }

        if (stream != null) {
            BufferedImage img = ImageIO.read(stream);
            stream.close();
            return img;
        }

        throw new RuntimeException("Could not parse image, image stream is missing");
    }
}
