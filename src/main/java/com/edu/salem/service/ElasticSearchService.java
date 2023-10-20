package com.edu.salem.service;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

@Service
public class ElasticSearchService implements SearchService {

	private final ElasticsearchClient client;
	private static final Logger logger = LoggerFactory.getLogger(ElasticsearchClient.class);

	public ElasticSearchService() {
		RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200))
				.build();

		ElasticsearchTransport transport = new RestClientTransport(restClient,
				new JacksonJsonpMapper());

		this.client = new ElasticsearchClient(transport);

	}

	public String simpleQuery(final String term) {

		if (this.client == null) {
			return term;
		}

		final SearchResponse<String> search;
		try {
			search = client.search(
					s -> s.index("products").query(q -> q.term(t -> t.field("name")
							.value(v -> v.stringValue(term)))),
					String.class);
			return search.toString();
		} catch (ElasticsearchException | IOException e) {
			logger.error("Error Occurred, ", e);
		}
		return null;
	}

}
