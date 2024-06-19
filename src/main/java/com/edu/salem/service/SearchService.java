package com.edu.salem.service;

import com.edu.salem.model.ComplexQueryRequestModel;
import com.edu.salem.model.SearchResponseModel;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Service
public interface SearchService {
    Optional<SearchResponseModel> complexQuery(final ComplexQueryRequestModel complexQueryRequestModel) throws IOException;
}
