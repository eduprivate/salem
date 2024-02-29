package com.edu.salem.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class SearchResponseModel implements Serializable {

    private Long hits;
    private List<Product> products;
    Map<String, Map<String, Long>> filters;

    public SearchResponseModel(Long hits, List<Product> products) {
        this.hits = hits;
        this.products = products;
    }

    public SearchResponseModel(Long hits, List<Product> products, Map<String, Map<String, Long>> filters) {
        this.hits = hits;
        this.products = products;
        this.filters = filters;
    }

    public SearchResponseModel(List<Product> products) {
        this.products = products;
    }

    public SearchResponseModel(Map<String, Map<String, Long>> filters) {
        this.filters = filters;
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

    public Long getHits() {
        return hits;
    }

    public void setHits(Long hits) {
        this.hits = hits;
    }
}
