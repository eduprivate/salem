package com.edu.salem.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
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
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
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
public class ElasticSearchService implements SearchService {

    private final String index;

    private final Double tieBreaker;
    private final ElasticsearchClient client;

    private final List<String> defaultFields = Arrays.asList("title", "entity");
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchService.class);

    public ElasticSearchService(@Value("${management.data.elasticsearch.productIndex}") final String index,
                                @Value("${management.data.elasticsearch.host}") final String host,
                                @Value("${management.data.elasticsearch.port}") final Integer port,
                                @Value("${management.data.elasticsearch.user}") final String user,
                                @Value("${management.data.elasticsearch.password}") final String password,
                                @Value("${management.data.elasticsearch.tieBreaker}") final Double tieBreaker) {

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

        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

        this.client = new ElasticsearchClient(transport);
    }

    @CircuitBreaker(name = "simpleQuery", fallbackMethod = "simpleQueryFallBack")
    public Optional<SearchResponseModel> simpleQuery(final String term) {
        List<Product> results = new ArrayList<>();
        if (this.client == null) {
            return Optional.empty();
        }

        final SearchResponse<Product> search;
        try {
            search = client.search(
                    s -> s.index(this.index).query(q -> q
                            .match(t -> t
                                    .field("title")
                                    .query(term)
                            )
                    ),
                    Product.class);
            logger.info("Service: Received query term", term);

            final SearchResponseModel searchResponseModel = esToModelConversion(search, null);

            return Optional.of(searchResponseModel);
        } catch (ElasticsearchException | IOException e) {
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

        } catch (ElasticsearchException | IOException e) {
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

        } catch (ElasticsearchException | IOException e) {
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
                    filtersValues.put(bucket.key().stringValue(), bucket.docCount());
                }
                filters.put(filterName, filtersValues);
            }

        }

        return new SearchResponseModel(totalHits, products, filters);
    }
}
