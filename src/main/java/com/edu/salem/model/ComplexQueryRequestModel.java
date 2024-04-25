package com.edu.salem.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class ComplexQueryRequestModel implements Serializable {
    private String queryTerm;
    private Map<String, String> filters;
    private Order order;

    public ComplexQueryRequestModel(String query, Map<String, String> filters, Order order) {
        this.queryTerm = query;
        this.filters = filters;
        this.order = order;
    }

    public String getQueryTerm() {
        return queryTerm;
    }

    public void setQueryTerm(String queryTerm) {
        this.queryTerm = queryTerm;
    }

    public Map<String, String> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, String> filters) {
        this.filters = filters;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryTerm, filters, order);
    }
}
