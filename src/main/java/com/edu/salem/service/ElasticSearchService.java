package com.edu.salem.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.json.JsonData;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ElasticSearchService implements SearchService {

    private final String index;
    private final ElasticsearchClient client;
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
                    s -> s.index(this.index).query(q -> q.term(t -> t.field("name")
                            .value(v -> v.stringValue(term)))),
                    String.class);
            return search.toString();
        } catch (ElasticsearchException | IOException e) {
            logger.error("Error Occurred, ", e);
        }
        return null;
    }

    @Override
    @HystrixCommand(commandKey = "complexQuery",fallbackMethod = "cachedComplexQuery", ignoreExceptions = { IOException.class })
    public Optional<SearchResponseModel> complexQuery(ComplexQueryRequestModel complexQueryRequestModel) throws IOException {
        final SearchResponse<String> search = client.search(
                s -> s.index("relevance")
                        .query(q -> q.term(t -> t.field("title")
                                .value(v -> v.stringValue(complexQueryRequestModel.getQueryTerm())))),
                String.class);

        final SearchResponseModel searchResponseModel = esToModelConversion(search);

        return Optional.of(searchResponseModel);
    }

    public Optional<SearchResponseModel> cachedComplexQuery() {
        return Optional.empty();
    }

    private SearchResponseModel esToModelConversion(SearchResponse<String> search) {
        final HitsMetadata<String> hits = search.hits();
        final List<Hit<String>> hitList = hits.hits();
        final List<Product> products = new ArrayList<>();

        for (Hit<String> hit : hitList) {
            final Map<String, JsonData> fields = hit.fields();
            Product product = new Product(fields.get("id").toString(),
                    fields.get("title").toString(),
                    fields.get("category").toString(),
                    fields.get("entity").toString());
            products.add(product);
        }

        return new SearchResponseModel(products);
    }

}
