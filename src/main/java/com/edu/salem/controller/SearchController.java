package com.edu.salem.controller;

import com.edu.salem.model.ComplexQueryRequestModel;
import com.edu.salem.model.SearchResponseModel;
import com.edu.salem.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Optional;

@RestController
public class SearchController {
	private final SearchService searchService;
    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    
    public SearchController(final SearchService searchService) {
		this.searchService = searchService;
	}
    
    @GetMapping(value = "/query/{term}")
    public ResponseEntity<String> simpleQuery(
            @PathVariable("term") final String term) {
        logger.info("Received query term", term);
        String response = "";
        try {
            response = this.searchService.simpleQuery(term);
            throw new IOException();
        } catch (IOException e) {
            logger.error("Error occurred.");
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping(value = "/query")
    public ResponseEntity<SearchResponseModel> complexQuery(
            @RequestBody final ComplexQueryRequestModel complexQueryRequestModel) {
        logger.info("Received query term", complexQueryRequestModel);
        Optional<SearchResponseModel> optionalSearchResponse = Optional.empty();
        try {
            optionalSearchResponse = this.searchService.complexQuery(complexQueryRequestModel);
            throw new IOException();
        } catch (IOException e) {
            logger.error("Error occurred.");
        }

        return new ResponseEntity<>(optionalSearchResponse.get(), HttpStatus.OK);
    }
}
