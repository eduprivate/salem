package com.edu.salem.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.edu.salem.service.SearchService;

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
        System.out.println(this.searchService.simpleQuery(term));
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }
}
