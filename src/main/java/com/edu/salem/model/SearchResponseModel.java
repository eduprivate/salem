package com.edu.salem.model;

import java.io.Serializable;
import java.util.List;

public class SearchResponseModel implements Serializable {
    private List<Product> products;

    public SearchResponseModel(List<Product> products) {
        this.products = products;
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }
}
