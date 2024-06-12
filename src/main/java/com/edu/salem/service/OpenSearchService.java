package com.edu.salem.service;


import com.edu.salem.model.ComplexQueryRequestModel;
import com.edu.salem.model.Product;
import com.edu.salem.model.SearchResponseModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.MultiMatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class OpenSearchService implements SearchService {

    private final String index;

    private final Double tieBreaker;
    private final OpenSearchClient client;

    private final List<String> defaultFields = Arrays.asList("title", "entity");
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchService.class);

    public OpenSearchService(@Value("${management.data.openSearch.productIndex}") final String index,
                                @Value("${management.data.openSearch.host}") final String host,
                                @Value("${management.data.openSearch.port}") final Integer port,
                                @Value("${management.data.openSearch.user}") final String user,
                                @Value("${management.data.openSearch.password}") final String password,
                                @Value("${management.data.openSearch.tieBreaker}") final Double tieBreaker) {

        this.index = index;

        this.tieBreaker = tieBreaker;

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));

        RestClientBuilder builder = RestClient.builder(
                        new HttpHost(host, port))
                .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                    @Override
                    public HttpAsyncClientBuilder customizeHttpClient(
                            HttpAsyncClientBuilder httpClientBuilder) {
                        return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                });

        final RestClient restClient = builder.build();
        final OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

        this.client = new OpenSearchClient(transport);
    }

    @CircuitBreaker(name = "simpleQuery", fallbackMethod = "simpleQueryFallBack")
    public Optional<SearchResponseModel> simpleQuery(final String term) {
        final SearchResponse<Product> search;
        try {
            search = client.search(
                    s -> s.index(this.index).query(q -> q
                            .match(t -> t
                                    .field("title")
                                    .query(FieldValue.of(term))
                            )
                    ),
                    Product.class);
            logger.info("Service: Received query term", term);

            return Optional.of(esToModelConversion(search, null));
        } catch (OpenSearchException | IOException e) {
            logger.error("Error Occurred, ", e);
        }
        return Optional.empty();
    }

    public Optional<SearchResponseModel> simpleQueryFallBack(final String term) {
        return Optional.empty();
    }

    @CircuitBreaker(name = "complexQuery", fallbackMethod = "complexQueryFallBack")
    @Override
    public Optional<SearchResponseModel> complexQuery(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {
        try {
            final CompletableFuture<SearchResponse<Product>> queryResultFuture = getQueryResult(complexQueryRequestModel);
            final CompletableFuture<SearchResponse<String>> queryAggregationResultFuture = getQueryAggregationResult(complexQueryRequestModel);

            final SearchResponse<Product> queryResult = queryResultFuture.get();
            final SearchResponse<String> queryAggregationResult = queryAggregationResultFuture.get();

            final SearchResponseModel searchResponseModel = esToModelConversion(queryResult, queryAggregationResult);

            return Optional.of(searchResponseModel);
        } catch (Throwable e) {
            logger.error("Error Occurred, ", e);
        }
        return Optional.empty();

    }

    public Optional<SearchResponseModel> complexQueryFallBack(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {
        return Optional.empty();
    }

    @Async
    private CompletableFuture<SearchResponse<Product>> getQueryResult(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {
        try {
            Query query = MultiMatchQuery.of(m -> m
                    .fields(defaultFields)
                    .operator(Operator.And)
                    .tieBreaker(tieBreaker)
                    .type(TextQueryType.CrossFields)
                    .query(complexQueryRequestModel.getQueryTerm())
            )._toQuery();

            return CompletableFuture.completedFuture(client.search(s -> s
                            .index(this.index)
                            .query(query),
                    Product.class
            ));

        } catch (OpenSearchException | IOException e) {
            logger.error("Error Occurred, ", e);
        }
        return null;
    }

    @Async
    private CompletableFuture<SearchResponse<String>> getQueryAggregationResult(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {
        try {
            Query query = MultiMatchQuery.of(m -> m
                    .fields(defaultFields)
                    .operator(Operator.And)
                    .tieBreaker(tieBreaker)
                    .type(TextQueryType.CrossFields)
                    .query(complexQueryRequestModel.getQueryTerm())
            )._toQuery();

            Map<String, Aggregation> filters = new HashMap<>();
            Aggregation aggregationCategory = new Aggregation.Builder()
                    .terms(new TermsAggregation.Builder().field("category").build())
                    .build();

            filters.put("category", aggregationCategory);

            return CompletableFuture.completedFuture(client.search(s -> s
                            .index(this.index)
                            .query(query)
                            .size(0)
                            .aggregations(filters),
                    String.class
            ));

        } catch (OpenSearchException | IOException e) {
            logger.error("Error Occurred, ", e);
        }
        return null;

    }

    public Optional<SearchResponseModel> cachedComplexQuery() {
        return Optional.empty();
    }

    private SearchResponseModel esToModelConversion(SearchResponse<Product> searchResults,
                                                    final SearchResponse<String> searchResultsAggregations) {
        final HitsMetadata<Product> hits = searchResults.hits();

        final List<Hit<Product>> hitList = hits.hits().stream()
                .collect(Collectors.toList());

        final List<Product> products = hitList.stream().map(hit -> hit.source()).toList();
        Long totalHits = searchResults.hits().total().value();

        Map<String, Map<String, Long>> filters = null;
        if (searchResultsAggregations != null) {

            filters = new HashMap<>();

            List<String> filterNames = new ArrayList<>(Arrays.asList("category"));

            for (String filterName : filterNames) {
                Map<String, Long> filtersValues = new HashMap<>();
                final List<StringTermsBucket> buckets = searchResultsAggregations.aggregations()
                        .get(filterName)
                        .sterms().buckets().array();

                for (StringTermsBucket bucket : buckets) {
                    filtersValues.put(bucket.key().toString(), bucket.docCount());
                }
                filters.put(filterName, filtersValues);
            }
        }

        return new SearchResponseModel(totalHits, products, filters);
    }
}
