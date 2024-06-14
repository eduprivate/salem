package com.edu.salem.service;


import com.edu.salem.model.ComplexQueryRequestModel;
import com.edu.salem.model.PaginationModel;
import com.edu.salem.model.Product;
import com.edu.salem.model.SearchResponseModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
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
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
@Primary
public class OpenSearchService implements SearchService {

    public static final String ERROR_OCCURRED = "Error Occurred, ";
    private final String index;

    private final Double tieBreaker;
    private final OpenSearchClient client;
    private final List<String> defaultFields = Arrays.asList("title", "entity");
    private static final Logger logger = LoggerFactory.getLogger(OpenSearchService.class);

    public OpenSearchService(@Value("${management.data.openSearch.productIndex}") final String index,
                                @Value("${management.data.openSearch.host}") final String host,
                                @Value("${management.data.openSearch.port}") final Integer port,
                                @Value("${management.data.openSearch.user}") final String user,
                                @Value("${management.data.openSearch.password}") final String password,
                                @Value("${management.data.openSearch.tieBreaker}") final Double tieBreaker) {

        this.index = index;

        this.tieBreaker = tieBreaker;


        final HttpHost httpHost = new HttpHost(host, port, "http");
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(httpHost), new UsernamePasswordCredentials(user, password));

        final RestClient restClient = RestClient.builder(httpHost).
                setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                    @Override
                    public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                        return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                }).build();

        final OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.client = new OpenSearchClient(transport);

    }

    @CircuitBreaker(name = "simpleQueryOS", fallbackMethod = "simpleQueryFallBack")
    public Optional<SearchResponseModel> simpleQuery(final String term) {
        SearchResponse<Product> search = null;
        try {
            search = client.search(
                    s -> s.index(this.index)
                            .size(60)
                            .from(0)
                            .query(q -> q
                            .match(t -> t
                                    .field("title")
                                    .query(FieldValue.of(term))
                            )
                    ),
                    Product.class);
            logger.info(String.format("Service: Received query term: %s", term));


        } catch (OpenSearchException | IOException e) {
            logger.error(ERROR_OCCURRED, e);
        }
        return Optional.ofNullable(toModelConversion(Optional.ofNullable(search), null, null));
    }

    public Optional<SearchResponseModel> simpleQueryFallBack(final String term) {
        return Optional.empty();
    }

    @NonNull
    @CircuitBreaker(name = "complexQuery", fallbackMethod = "complexQueryFallBack")
    @Override
    public Optional<SearchResponseModel> complexQuery(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {

        SearchResponseModel searchResponseModel = null;
        try {
            final CompletableFuture<SearchResponse<Product>> queryResultFuture = getQueryResult(complexQueryRequestModel);
            final CompletableFuture<SearchResponse<String>> queryAggregationResultFuture = getQueryAggregationResult(complexQueryRequestModel);

            final SearchResponse<Product> queryResult = queryResultFuture.get();
            final SearchResponse<String> queryAggregationResult = queryAggregationResultFuture.get();

            searchResponseModel = toModelConversion(Optional.ofNullable(queryResult), queryAggregationResult, complexQueryRequestModel);

        } catch (InterruptedException e) {
            logger.error(ERROR_OCCURRED, e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        return Optional.ofNullable(searchResponseModel);

    }

    public Optional<SearchResponseModel> complexQueryFallBack(ComplexQueryRequestModel complexQueryRequestModel) {
        logger.info(String.format("Open Fallback for query: %s", complexQueryRequestModel.getQueryTerm()));
        return Optional.empty();
    }

    @Async
    private CompletableFuture<SearchResponse<Product>> getQueryResult(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {
        try {

            Query queryFinal = buildQuery(complexQueryRequestModel);

            return CompletableFuture.completedFuture(client.search(s -> s
                            .index(this.index)
                            .size(complexQueryRequestModel.getSize())
                            .from(complexQueryRequestModel.getFrom())
                            .query(queryFinal),
                    Product.class
            ));

        } catch (OpenSearchException | IOException e) {
            logger.error(ERROR_OCCURRED, e);
        }
        return null;
    }

    private Query buildQuery(final ComplexQueryRequestModel complexQueryRequestModel) {

        BoolQuery filterQueries = buildFilterQueries(complexQueryRequestModel);

        Query query = MultiMatchQuery.of(m -> m
                .fields(defaultFields)
                .operator(Operator.And)
                .tieBreaker(tieBreaker)
                .type(TextQueryType.CrossFields)
                .query(complexQueryRequestModel.getQueryTerm())
        ).toQuery();

        BoolQuery queryComplex =
                QueryBuilders.bool()
                        .must(query)
                        .filter(filterQueries.filter()).build();

        return new Query.Builder()
                .bool(queryComplex)
                .build();
    }

    private static BoolQuery buildFilterQueries(final ComplexQueryRequestModel complexQueryRequestModel) {

        final Map<String, String> filters = complexQueryRequestModel.getFilters();
        List<Query> queries = new ArrayList<>();

        for (Map.Entry<String, String> filter : filters.entrySet() ) {
            List<TermsQuery> terms = new ArrayList<>();
            List<FieldValue> fieldValues = new ArrayList<>();
            fieldValues.add(FieldValue.of(filter.getValue()));

            TermsQuery termsQuery = TermsQuery.of(ts -> ts
                    .field(filter.getKey())
                    .terms(TermsQueryField.of(t -> t.value(fieldValues))));
            terms.add(termsQuery);

            Query query = new Query.Builder()
                    .terms(termsQuery)
                    .build();
            queries.add(query);
        }

        BoolQuery.Builder boolQuery = new BoolQuery.Builder().filter(queries);
        return boolQuery.build();
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
            ).toQuery();

            Map<String, Aggregation> filters = new HashMap<>();

            List<String> attributes = Arrays.stream(new String[]{"category", "entity"}).toList();

            for (String attribute :attributes ) {
                Aggregation aggregationCategory = new Aggregation.Builder()
                        .terms(new TermsAggregation.Builder().field(attribute).build())
                        .build();
                filters.put(attribute, aggregationCategory);
            }

            return CompletableFuture.completedFuture(client.search(s -> s
                            .index(this.index)
                            .query(query)
                            .size(0)
                            .aggregations(filters),
                    String.class
            ));

        } catch (OpenSearchException | IOException e) {
            logger.error(ERROR_OCCURRED, e);
        }
        return null;

    }

    public Optional<SearchResponseModel> cachedComplexQuery() {
        return Optional.empty();
    }

    private SearchResponseModel toModelConversion(Optional<SearchResponse<Product>> optionalSearchResponse,
                                                  final SearchResponse<String> searchResultsAggregations, ComplexQueryRequestModel complexQueryRequestModel) {
        if (optionalSearchResponse.isPresent()) {

            final SearchResponse<Product> searchResults = optionalSearchResponse.get();

            final HitsMetadata<Product> hits = searchResults.hits();

            final List<Hit<Product>> hitList = hits.hits().stream().toList();

            final List<Product> products = hitList.stream().map(Hit::source).toList();
            Long totalHits = searchResults.hits().total().value();

            Map<String, Map<String, Long>> filters = null;
            if (searchResultsAggregations != null) {

                filters = new HashMap<>();

                List<String> filterNames = new ArrayList<>(Arrays.asList("category", "entity"));

                for (String filterName : filterNames) {
                    Map<String, Long> filtersValues = new HashMap<>();
                    final List<StringTermsBucket> buckets = searchResultsAggregations.aggregations()
                            .get(filterName)
                            .sterms().buckets().array();

                    for (StringTermsBucket bucket : buckets) {
                        filtersValues.put(bucket.key(), bucket.docCount());
                    }
                    filters.put(filterName, filtersValues);
                }
            }

            PaginationModel paginationModel = new PaginationModel(complexQueryRequestModel.getSize(),
                    complexQueryRequestModel.getFrom());

            return new SearchResponseModel.Builder(totalHits, products, filters).setPaginationModel(paginationModel).build();
        } else {
            return new SearchResponseModel.Builder(0L, new ArrayList<>(), null).build();
        }

    }
}
