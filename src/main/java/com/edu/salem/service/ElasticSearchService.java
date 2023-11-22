package com.edu.salem.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.edu.salem.model.ComplexQueryRequestModel;
import com.edu.salem.model.Product;
import com.edu.salem.model.SearchResponseModel;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ElasticSearchService implements SearchService {

    private final String index;
    private final ElasticsearchClient client;

    private final List<String> defaultFields = Arrays.asList("title", "entity");
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchService.class);

    public ElasticSearchService(@Value("${spring.elasticsearch.productIndex}") final String index,
                                @Value("${spring.elasticsearch.host}") final String host,
                                @Value("${spring.elasticsearch.port}") final Integer port) {

        this.index = index;
        RestClient restClient = RestClient.builder(new HttpHost(host, port))
                .build();

        ElasticsearchTransport transport = new RestClientTransport(restClient,
                new JacksonJsonpMapper());

        this.client = new ElasticsearchClient(transport);

    }

    public String simpleQuery(final String term) {

        if (this.client == null) {
            return term;
        }

        final SearchResponse<String> search;
        try {
            search = client.search(
                    s -> s.index(this.index).query(q -> q.term(t -> t.field(defaultFields.get(0))
                            .value(v -> v.stringValue(term)))),
                    String.class);
            return search.toString();
        } catch (ElasticsearchException | IOException e) {
            logger.error("Error Occurred, ", e);
        }
        return null;
    }

    @Override
    @HystrixCommand(commandKey = "complexQuery", fallbackMethod = "cachedComplexQuery", ignoreExceptions = {IOException.class})
    public Optional<SearchResponseModel> complexQuery(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {

        final MultiMatchQuery.Builder builder = QueryBuilders.multiMatch()
                .query(complexQueryRequestModel.getQueryTerm())
                .fields(defaultFields)
                .operator(Operator.And)
                .tieBreaker(0.7)
                .type(TextQueryType.CrossFields);

        final SearchResponse<Product> search = client.search(s -> s
                        .index(this.index)
                        .query(builder.build()._toQuery()),
                Product.class
        );
        final SearchResponseModel searchResponseModel = esToModelConversion(search);

        return Optional.of(searchResponseModel);
    }

    public Optional<SearchResponseModel> cachedComplexQuery() {
        return Optional.empty();
    }

    private SearchResponseModel esToModelConversion(SearchResponse<Product> search) {
        final HitsMetadata<Product> hits = search.hits();

        final List<Hit<Product>> hitList = hits.hits().stream()
                .collect(Collectors.toList());

        final List<Product> products = hitList.stream().map(
                productHit -> new Product(productHit.fields().get("id").toString(),
                        productHit.fields().get("title").toString(),
                        productHit.fields().get("category").toString(),
                        productHit.fields().get("entity").toString())
        ).collect(Collectors.toList());

        return new SearchResponseModel(products);
    }
}
