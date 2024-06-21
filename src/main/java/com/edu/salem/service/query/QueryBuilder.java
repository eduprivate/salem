package com.edu.salem.service.query;

import com.edu.salem.model.ComplexQueryRequestModel;
import com.edu.salem.model.PaginationModel;
import com.edu.salem.model.Product;
import com.edu.salem.model.SearchResponseModel;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class QueryBuilder {
    private static final List<String> DEFAULT_SEARCHABLE_FIELDS = Arrays.asList("title", "entity");

    private static final List<String> DEFAULT_AGGREGATION_FIELDS = Arrays.asList("category", "entity");
    private final Double tieBreaker;

    public QueryBuilder(@Value("${management.data.openSearch.tieBreaker}") final Double tieBreaker) {
        this.tieBreaker = tieBreaker;
    }

    public Query buildQuery(final ComplexQueryRequestModel complexQueryRequestModel) {

        BoolQuery filterQueries = buildFilterQueries(complexQueryRequestModel);

        Query query = MultiMatchQuery.of(m -> m
                .fields(DEFAULT_SEARCHABLE_FIELDS)
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

    public static BoolQuery buildFilterQueries(final ComplexQueryRequestModel complexQueryRequestModel) {

        final Map<String, String> filters = complexQueryRequestModel.getFilters();
        List<Query> queries = new ArrayList<>();

        for (Map.Entry<String, String> filter : filters.entrySet()) {
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

    public Map<String, Aggregation> buildAggregationFilters() {
        Map<String, Aggregation> filters = new HashMap<>();

        for (String attribute : DEFAULT_AGGREGATION_FIELDS) {
            Aggregation aggregationCategory = new Aggregation.Builder()
                    .terms(new TermsAggregation.Builder().field(attribute).build())
                    .build();
            filters.put(attribute, aggregationCategory);
        }
        return filters;
    }

    public SearchResponseModel toModelConversion(final Optional<SearchResponse<Product>> optionalSearchResponse,
                                                 final SearchResponse<String> searchResultsAggregations,
                                                 final ComplexQueryRequestModel complexQueryRequestModel) {
        if (optionalSearchResponse.isPresent()) {

            final SearchResponse<Product> searchResults = optionalSearchResponse.get();
            final HitsMetadata<Product> hits = searchResults.hits();
            final List<Hit<Product>> hitList = hits.hits().stream().toList();
            final List<Product> products = hitList.stream().map(Hit::source).toList();
            final Long totalHits = searchResults.hits().total().value();

            Map<String, Map<String, Long>> filters = null;
            if (searchResultsAggregations != null) {

                filters = new HashMap<>();

                for (String filterName : DEFAULT_AGGREGATION_FIELDS) {
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

            PaginationModel paginationModel = null;
            if (complexQueryRequestModel != null) {
                paginationModel = new PaginationModel(complexQueryRequestModel.getSize(),
                        complexQueryRequestModel.getFrom());
            }

            return new SearchResponseModel.Builder(totalHits, products, filters).setPaginationModel(paginationModel).build();
        } else {
            return new SearchResponseModel.Builder(0L, new ArrayList<>(), null).build();
        }

    }
}
