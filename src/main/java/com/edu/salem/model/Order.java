package com.edu.salem.model;

public enum Order {
    ASC("asc"),
    DESC("desc");

    private String type;

    Order(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
