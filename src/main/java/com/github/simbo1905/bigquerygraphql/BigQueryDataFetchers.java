package com.github.simbo1905.bigquerygraphql;

import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryParameterValue;
import graphql.schema.DataFetcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class BigQueryDataFetchers {

    @Autowired
    BigQueryRunner bigQueryRunner = null;

    @Value("${query.bookById}")
    String bookByIdQuery;

    @Value("${query.authorById}")
    String authorByIdQuery;

    @Value("${mapper.bookByIdCsv}")
    String bookByIdMapperCsv;

    @Value("${mapper.authorByIdCsv}")
    String authorByIdMapperCsv;

    /**
     * Creates code to load a BigQuery FieldValueList result into a map.
     * Regrettably FieldValueList is more like a map where it hides the names of the keys!
     * So we use external config to name the fields in the queries and this logic to
     * actually turn the result set into a map.
     * @param fieldsCsv The list of map keys with corresponding values in the oddly named FieldValueList
     * @return A function that can load specific query results into a map.
     */
    Function<FieldValueList, Map<String, String>> mapperFor(final String fieldsCsv) {
        final String[] fields = fieldsCsv.split(",");
        return (fieldValues -> {
            Map<String, String> result = new LinkedHashMap<>();
            Arrays.stream(fields).forEach(field->result.put(field, fieldValues.get(field).getStringValue()));
            return result;
        });
    }

    @PostConstruct
    public void logConfig() {
        log.info("bookByIdQuery: {}", bookByIdQuery);
        log.info("authorByIdQuery: {}", authorByIdQuery);
        log.info("bookByIdMapperCsv: {}", bookByIdMapperCsv);
        log.info("authorByIdMapperCsv: {}", authorByIdMapperCsv);
    }

    public DataFetcher getBookByIdDataFetcher() {
        // we need a mapper for the query
        final Function<FieldValueList, Map<String, String>> bookMapper = mapperFor(this.bookByIdMapperCsv);
        // return a lambda that runs the query and applies the mapper to get the result as a GraphQL friendly Map
        return dataFetchingEnvironment -> {
            String bookId = dataFetchingEnvironment.getArgument("id");
            Map<String, QueryParameterValue> parameterValueMap = new LinkedHashMap<>();
            parameterValueMap.put("id", QueryParameterValue.string(bookId));
            return bigQueryRunner.queryAndWaitFor(bookByIdQuery, bookMapper, parameterValueMap)
                    .stream()
                    .findFirst()
                    .orElse(null);
        };
    }

    public DataFetcher getAuthorDataFetcher() {
        // we need a mapper for the query
        final Function<FieldValueList, Map<String, String>> authorMapper = mapperFor(this.authorByIdMapperCsv);
        // return a lambda that runs the query and applies the mapper to get the result as a GraphQL friendly Map
        return dataFetchingEnvironment -> {
            Map<String, String> book = dataFetchingEnvironment.getSource();
            String authorId = book.get("authorId");
            Map<String, QueryParameterValue> parameterValueMap = new LinkedHashMap<>();
            parameterValueMap.put("id", QueryParameterValue.string(authorId));
            return bigQueryRunner.queryAndWaitFor(authorByIdQuery, authorMapper, parameterValueMap)
                    .stream()
                    .findFirst()
                    .orElse(null);
        };
    }
}
