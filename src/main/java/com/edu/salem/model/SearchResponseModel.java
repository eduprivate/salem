package com.edu.salem.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class SearchResponseModel implements Serializable {

    private Long hits;
    private List<Product> products;
    private Map<String, Map<String, Long>> filters;

    public SearchResponseModel(Long hits, List<Product> products, Map<String, Map<String, Long>> filters) {
        this.hits = hits;
        this.products = products;
        this.filters = filters;
    }

}
