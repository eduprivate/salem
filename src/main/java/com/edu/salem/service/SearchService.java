package com.edu.salem.service;

import org.springframework.stereotype.Service;

@Service
public interface SearchService {
	public String simpleQuery(final String term);
}
