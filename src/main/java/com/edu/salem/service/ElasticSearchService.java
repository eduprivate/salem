package com.edu.salem.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
                        return httpClientBuilder
                                .setDefaultCredentialsProvider(credentialsProvider);
                    }
                });

        final RestClient restClient = builder.build();

        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

        this.client = new ElasticsearchClient(transport);

    }


    public String simpleQueryFallBack(final String term) {
        return "Fallback:" + term;
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

            final SearchResponseModel searchResponseModel = esToModelConversion(search);

            return Optional.of(searchResponseModel);
        } catch (ElasticsearchException | IOException e) {
            logger.error("Error Occurred, ", e);
        }
        return Optional.empty();

    }

    @Override
    public Optional<SearchResponseModel> complexQuery(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {
        try {
            Query query = MultiMatchQuery.of(m -> m
                    .fields(defaultFields)
                    .operator(Operator.And)
                    .tieBreaker(tieBreaker)
                    .type(TextQueryType.CrossFields)
                    .query(complexQueryRequestModel.getQueryTerm())
            )._toQuery();

            final SearchResponse<Product> search = client.search(s -> s
                            .index(this.index)
                            .query(query),
                    Product.class
            );

            final SearchResponseModel searchResponseModel = esToModelConversion(search);

            return Optional.of(searchResponseModel);


        } catch (ElasticsearchException | IOException e) {
            logger.error("Error Occurred, ", e);
        }
        return Optional.empty();

    }

    public Optional<SearchResponseModel> cachedComplexQuery() {
        return Optional.empty();
    }

    private SearchResponseModel esToModelConversion(SearchResponse<Product> search) {
        final HitsMetadata<Product> hits = search.hits();

        final List<Hit<Product>> hitList = hits.hits().stream()
                .collect(Collectors.toList());

        final List<Product> products = hitList.stream().map(hit -> hit.source()).toList();

        return new SearchResponseModel(products);
    }
}
