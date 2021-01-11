package com.github.simbo1905.bigquerygraphql;

import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryParameterValue;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
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

    /**
     * This method copies the source attribute to the dest attribute as a BQ parameter value map.
     * It current assumes you want to query by a String. It should be extended to query by other types.
     * It resolves the source attribute from the DataFetchingEnvironment by first checking if there is
     * a source entity. If there is a source it calls <pre>entity.get(sourceAttr)</pre>. If there is no
     * source entity it calls <pre>dataFetchingEnvironment.getArgument(sourceAttr)</pre>
     *
     * @param dataFetchingEnvironment The context of where the query is being invoked.
     * @param sourceAttr The name of the source attribute e.g., "id" or "authorId".
     * @param destAttr The name of the destination attribute, typically "id".
     * @return A BQ named parameters map.
     */
    private Map<String, QueryParameterValue> resolveQueryParams(DataFetchingEnvironment dataFetchingEnvironment,
                                                                String sourceAttr,
                                                                String destAttr) {
        Map<String, String> entity = dataFetchingEnvironment.getSource();
        String authorId = (entity != null) ? entity.get(sourceAttr) : dataFetchingEnvironment.getArgument(sourceAttr);
        Map<String, QueryParameterValue> parameterValueMap = new LinkedHashMap<>();
        parameterValueMap.put(destAttr, QueryParameterValue.string(authorId));
        return parameterValueMap;
    }

    public DataFetcher queryForOne(String query, String mappingCsv, String source, String dest) {
        // we need a mapper for the query. regrettably BigQuery doesn't let you know what is in a result you have to know
        final Function<FieldValueList, Map<String, String>> authorMapper = mapperFor(mappingCsv);
        // return a lambda that runs the query and applies the mapper to get the result as a GraphQL friendly Map
        return dataFetchingEnvironment -> {
            Map<String, QueryParameterValue> parameterValueMap = resolveQueryParams(dataFetchingEnvironment, source, dest);
            return bigQueryRunner.query(query, authorMapper, parameterValueMap)
                    .stream()
                    .findFirst()
                    .orElse(null);
        };
    }


}
