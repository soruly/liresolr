package net.semanticmetadata.lire.solr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Streams;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;


public final class HashFrequenciesCache {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final HashMap<String, Map<Integer, Integer>> HASH_FREQUENCIES = new HashMap<>();

    public static int getHashFrequency(String field, int hash) {
        final var termStats = HASH_FREQUENCIES.get(field);
        return termStats != null
                ? termStats.getOrDefault(hash, 0)
                : 0;
    }

    public static void updateAll(String coreName) {
        for (String code : FeatureRegistry.getSupportedCodes()) {
            update(coreName, code + "_ha");
        }
    }

    public static void update(String coreName, String field) {
        // FIXME Allow updating when committed.
        if (HASH_FREQUENCIES.containsKey(field)) {
            return;
        }

        final var hashesCountCache = getFieldData(coreName, field)
                .map(HashFrequenciesCache::getResults)
                .orElseGet(Map::of);

        HASH_FREQUENCIES.put(field, hashesCountCache);
    }

    private static Map<Integer, Integer> getResults(JsonNode node) {
        var elements = node.get("facets")
                .get("categories")
                .get("buckets")
                .elements();

        return Streams.stream(elements)
                .collect(Collectors.toMap(
                        element -> element.get("val").asInt(),
                        element -> element.get("count").asInt())
                );
    }

    private static Optional<JsonNode> getFieldData(String coreName, String fieldName) {
        var queryUri = getFieldUri(coreName, fieldName);

        try {
            var httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(queryUri))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            var result = OBJECT_MAPPER.readTree(response.body());

            return Optional.ofNullable(result);
        } catch (Exception e) {
            System.err.println(e);
            System.err.println("Failed to get hashes info: " + queryUri);
        }

        return Optional.empty();
    }

    private static String getFieldUri(String core, String fieldName) {
        return "http://127.0.0.1:8983/solr/" + core + "/select?q=*:*&json.facet=" + URLEncoder.encode("{categories:{terms:{field:" + fieldName + ",limit:-1}}}", UTF_8) + "&rows=0&indent=false";
    }

}
