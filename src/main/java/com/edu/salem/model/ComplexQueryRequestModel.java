package com.edu.salem.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class ComplexQueryRequestModel implements Serializable {
    private static final Integer DEFAULT_SIZE = 60;
    private static final Integer DEFAULT_FROM = 0;
    private String queryTerm;
    private Map<String, String> filters;
    private Order order;

    private Integer size;

    private Integer from;

    public ComplexQueryRequestModel(String query, Map<String, String> filters, Order order) {
        this.queryTerm = query;
        this.filters = filters;
        this.order = order;
        this.size = DEFAULT_SIZE;
        this.from = DEFAULT_FROM;
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

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryTerm, filters, order);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComplexQueryRequestModel that = (ComplexQueryRequestModel) o;
        return Objects.equals(queryTerm, that.queryTerm) && Objects.equals(filters, that.filters) && order == that.order && Objects.equals(size, that.size) && Objects.equals(from, that.from);
    }
}
