package com.edu.salem.controller;

import com.edu.salem.model.ComplexQueryRequestModel;
import com.edu.salem.model.SearchResponseModel;
import com.edu.salem.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Optional;

@RestController
public class SearchController {
    private final SearchService searchService;
    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    public SearchController(final SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping(value = "/query")
    @Cacheable(value = "complexQuery")
    @Nullable
    public ResponseEntity<SearchResponseModel> complexQuery(
            @RequestBody final ComplexQueryRequestModel complexQueryRequestModel) {
        Optional<SearchResponseModel> optionalSearchResponse = Optional.empty();
        try {
            optionalSearchResponse = this.searchService.complexQuery(complexQueryRequestModel);
            if (optionalSearchResponse.isPresent()) {
                return new ResponseEntity<>(optionalSearchResponse.get(), HttpStatus.OK);
            }
        } catch (IOException e) {
            logger.error("Error occurred.");
        }

        return new ResponseEntity<>(null, HttpStatus.NO_CONTENT);
    }
}
