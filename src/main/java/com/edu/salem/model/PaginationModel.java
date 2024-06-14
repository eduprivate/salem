package com.edu.salem.model;

import java.io.Serializable;

public class PaginationModel implements Serializable {
    private Integer size;
    private Integer from;

    public PaginationModel(Integer size, Integer from) {
        this.size = size;
        this.from = from;
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


}
