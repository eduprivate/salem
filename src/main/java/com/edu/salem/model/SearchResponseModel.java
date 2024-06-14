package com.edu.salem.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class SearchResponseModel implements Serializable {

    private Long hits;
    private List<Product> products;
    private Map<String, Map<String, Long>> filters;
    private PaginationModel pagination;

    private SearchResponseModel(Builder builder) {
        this.hits = builder.hits;
        this.products = builder.products;
        this.filters = builder.filters;
        this.pagination = builder.pagination;
    }

    public Long getHits() {
        return hits;
    }

    public void setHits(Long hits) {
        this.hits = hits;
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }

    public Map<String, Map<String, Long>> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Map<String, Long>> filters) {
        this.filters = filters;
    }

    public PaginationModel getPagination() {
        return pagination;
    }

    public void setPagination(PaginationModel pagination) {
        this.pagination = pagination;
    }

    public static class Builder {
        private Long hits;
        private List<Product> products;
        private Map<String, Map<String, Long>> filters;
        private PaginationModel pagination;

        public Builder(Long hits, List<Product> products, Map<String, Map<String, Long>> filters) {
            this.hits = hits;
            this.products = products;
            this.filters = filters;
        }

        public Builder setPaginationModel(PaginationModel paginationModel) {
            this.pagination = paginationModel;
            return this;
        }

        public SearchResponseModel build() {
            return new SearchResponseModel(this);
        }
    }
}
