package com.edu.salem.model;

import java.io.Serializable;

public class Product implements Serializable {


    private String id;
    private String title;
    private String category;
    private String entity;

    public Product(String id, String title, String category, String entity) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.entity = entity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }
}
