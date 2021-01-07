package com.github.simbo1905.bigquerygraphql;

import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryParameterValue;
import graphql.schema.DataFetcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class BigQueryDataFetchers {

    @Autowired
    BigQueryRunner bigQueryRunner = null;

    Function<FieldValueList, Map<String, String>> bookMapper = (fieldValues -> {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("id", fieldValues.get("id").getStringValue());
        result.put("name", fieldValues.get("name").getStringValue());
        result.put("pageCount", fieldValues.get("pageCount").getStringValue());
        result.put("authorId", fieldValues.get("authorId").getStringValue());
        return result;
    });

    Function<FieldValueList, Map<String, String>> authorMapper = (fieldValues -> {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("id", fieldValues.get("id").getStringValue());
        result.put("firstName", fieldValues.get("firstName").getStringValue());
        result.put("lastName", fieldValues.get("lastName").getStringValue());
        return result;
    });

    @Value("${query.bookById}")
    String bookByIdQuery;

    @Value("${query.authorById}")
    String authorByIdQuery;

    @PostConstruct
    public void logConfig() {
        log.info("bookByIdQuery: {}", bookByIdQuery);
        log.info("authorByIdQuery: {}", authorByIdQuery);
    }

    public DataFetcher getBookByIdDataFetcher() {
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
