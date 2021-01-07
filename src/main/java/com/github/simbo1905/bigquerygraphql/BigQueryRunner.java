package com.github.simbo1905.bigquerygraphql;

import com.google.api.client.util.Value;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.*;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BigQueryRunner {

    BigQuery bigQuery = null;

    @Value("${gcp.project")
    String projectId;

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
    public <T> List<T> queryAndWaitFor(final String query,
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
}
