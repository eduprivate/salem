package com.edu.salem.service;


import com.edu.salem.model.ComplexQueryRequestModel;
import com.edu.salem.model.Product;
import com.edu.salem.model.SearchResponseModel;
import com.edu.salem.service.query.QueryBuilder;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
@Primary
public class OpenSearchService implements SearchService {

    private static final String ERROR_OCCURRED = "Error Occurred, ";
    private final String index;
    private final OpenSearchClient client;
    private final QueryBuilder queryBuilder;
    private static final Logger logger = LoggerFactory.getLogger(OpenSearchService.class);

    public OpenSearchService(@Value("${management.data.openSearch.productIndex}") final String index,
                             @Value("${management.data.openSearch.host}") final String host,
                             @Value("${management.data.openSearch.port}") final Integer port,
                             @Value("${management.data.openSearch.user}") final String user,
                             @Value("${management.data.openSearch.password}") final String password,
                             final QueryBuilder queryBuilder) {

        final HttpHost httpHost = new HttpHost(host, port, "http");
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(httpHost), new UsernamePasswordCredentials(user, password));

        final RestClient restClient = RestClient.builder(httpHost).
                setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(
                        credentialsProvider)).build();

        final OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.queryBuilder = queryBuilder;
        this.index = index;
        this.client = new OpenSearchClient(transport);

    }

    @NonNull
    @CircuitBreaker(name = "complexQuery", fallbackMethod = "complexQueryFallBack")
    @Override
    public Optional<SearchResponseModel> complexQuery(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {

        SearchResponseModel searchResponseModel = null;
        try {
            final CompletableFuture<SearchResponse<Product>> queryResultFuture = getQueryResult(complexQueryRequestModel);
            final CompletableFuture<SearchResponse<String>> queryAggregationResultFuture = getQueryAggregationResult(complexQueryRequestModel);

            final SearchResponse<Product> queryResult = queryResultFuture != null ? queryResultFuture.get() : null;
            final SearchResponse<String> queryAggregationResult = queryAggregationResultFuture != null ? queryAggregationResultFuture.get() : null;

            searchResponseModel = this.queryBuilder.toModelConversion(Optional.ofNullable(queryResult), queryAggregationResult, complexQueryRequestModel);

        } catch (InterruptedException e) {
            logger.error(ERROR_OCCURRED, e);
            Thread.currentThread().interrupt();
        } catch (NullPointerException | ExecutionException e) {
            logger.error(ERROR_OCCURRED, e);
        }
        return Optional.ofNullable(searchResponseModel);

    }

    @Async
    private CompletableFuture<SearchResponse<Product>> getQueryResult(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {
        try {

            Query queryFinal = this.queryBuilder.buildQuery(complexQueryRequestModel);

            return CompletableFuture.completedFuture(client.search(s -> s
                            .index(this.index)
                            .size(complexQueryRequestModel.getSize())
                            .from(complexQueryRequestModel.getFrom())
                            .query(queryFinal),
                    Product.class
            ));

        } catch (OpenSearchException e) {
            logger.error(ERROR_OCCURRED, e);
        }
        return null;
    }

    @Async
    private CompletableFuture<SearchResponse<String>> getQueryAggregationResult(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {
        try {
            final Query query = this.queryBuilder.buildAggregationQuery(complexQueryRequestModel);
            final Map<String, Aggregation> filters = this.queryBuilder.buildAggregationFilters();

            return CompletableFuture.completedFuture(client.search(s -> s
                            .index(this.index)
                            .query(query)
                            .size(0)
                            .aggregations(filters),
                    String.class
            ));

        } catch (OpenSearchException e) {
            logger.error(ERROR_OCCURRED, e);
        }
        return null;

    }

}
