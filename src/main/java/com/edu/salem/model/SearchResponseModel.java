package com.edu.salem.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class SearchResponseModel implements Serializable {

    private Long hits;
    private List<Product> products;
    private Map<Filter, String> filters;

    public SearchResponseModel(Long hits, List<Product> products) {
        this.hits = hits;
        this.products = products;
    }

    public SearchResponseModel(List<Product> products) {
        this.products = products;
    }

    public SearchResponseModel(Map<Filter, String> filters) {
        this.filters = filters;
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }

    public Map<Filter, String> getFilters() {
        return filters;
    }

    public void setFilters(Map<Filter, String> filters) {
        this.filters = filters;
    }

    public Long getHits() {
        return hits;
    }

    public void setHits(Long hits) {
        this.hits = hits;
    }
}
