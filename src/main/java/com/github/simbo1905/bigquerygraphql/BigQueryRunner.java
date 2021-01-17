package com.github.simbo1905.bigquerygraphql;

import com.google.api.client.util.Value;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.*;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BigQueryRunner {

    BigQuery bigQuery = null;

    @Value("${gcp.project")
    String projectId = "capable-conduit-300818";

    @Value("${gcp.bigquery.log.thresholdms}")
    long logThresholdMs = 1000;

    @SneakyThrows
    @PostConstruct
    protected void init() {
        log.info("Initialising BigQuery for project {}", projectId);
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        this.bigQuery = BigQueryOptions.getDefaultInstance().toBuilder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build()
                .getService();
    }

    @SneakyThrows
    public <T> List<T> query(final String query,
                             Function<FieldValueList, T> mapper,
                             Map<String, QueryParameterValue> parameterValueMap){
        final long startTime = System.currentTimeMillis();

        val logParams = logParams(parameterValueMap);
        log.info("running query {} with  params: [{}]", query, logParams);

        // create the query
        QueryJobConfiguration queryJobConfiguration = QueryJobConfiguration.newBuilder(query)
                .setUseLegacySql(false)
                .setNamedParameters(parameterValueMap)
                .build();

        // create and block on the job using waitFor()
        Job job = this.bigQuery.create(JobInfo.newBuilder(queryJobConfiguration).build()).waitFor();

        // you probably want to use some other metrics/gauges to understand actual performance.
        final float duration = (System.currentTimeMillis() - startTime);
        if( duration > logThresholdMs ) {
            log.warn("slow query ran in {} ms: {}, params: [{}]", Math.round(duration), query, logParams  );
        }

        // map the results into the desired DTO
        final List<T> result = new ArrayList<>();
        job.getQueryResults()
                .iterateAll()
                .forEach(fields->{
                    T t = mapper.apply(fields);
                    result.add(t);
                });
        return result;
    }

    private String logParams(Map<String, QueryParameterValue> parameterValueMap) {
        return parameterValueMap
                .entrySet()
                .stream()
                .map(e -> String.format("%s=%s", e.getKey(), e.getValue().toString()))
                .collect(Collectors.joining("|"));
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

    public DataFetcher queryForOne(String query, String mappingCsv, String sourceAttr, String destAttr) {
        // we need a mapper for the query. regrettably BigQuery doesn't let you know what is in a result you have to know
        final Function<FieldValueList, Map<String, String>> authorMapper = mapperFor(mappingCsv);
        // return a lambda that runs the query and applies the mapper to get the result as a GraphQL friendly Map
        return dataFetchingEnvironment -> {
            Map<String, QueryParameterValue> parameterValueMap = resolveQueryParams(dataFetchingEnvironment, sourceAttr, destAttr);
            return query(query, authorMapper, parameterValueMap)
                    .stream()
                    .findFirst()
                    .orElse(null);
        };
    }
}
