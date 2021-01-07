package com.github.simbo1905.bigquerygraphql;

import com.google.cloud.bigquery.QueryParameterValue;
import graphql.schema.DataFetcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BigQueryDataFetchers {

    @Autowired
    BigQueryRunner bigQueryRunner = null;

    @Value("${query.bookById")
    String bookByIdQuery;

    @Value("${query.authorById")
    String authorByIdQuery;

    public DataFetcher getBookByIdDataFetcher() {
        return dataFetchingEnvironment -> {
            String bookId = dataFetchingEnvironment.getArgument("id");
            Map<String, QueryParameterValue> parameterValueMap = new LinkedHashMap<>();
            parameterValueMap.put("id", QueryParameterValue.string(bookId));
            return bigQueryRunner.queryAndWaitFor(bookByIdQuery,null, parameterValueMap);
        };
    }

    public DataFetcher getAuthorDataFetcher() {
        return dataFetchingEnvironment -> {
            Map<String,String> book = dataFetchingEnvironment.getSource();
            String authorId = book.get("authorId");
            Map<String, QueryParameterValue> parameterValueMap = new LinkedHashMap<>();
            parameterValueMap.put("id", QueryParameterValue.string(authorId));
            return bigQueryRunner.queryAndWaitFor(authorByIdQuery,null, parameterValueMap);
        };
    }
}
