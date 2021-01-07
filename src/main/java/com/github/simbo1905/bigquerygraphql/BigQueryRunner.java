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
        // TODO test without jobId as we will fail-fast and not reattempt
        JobId jobId = JobId.of(UUID.randomUUID().toString());
        QueryJobConfiguration queryJobConfiguration = QueryJobConfiguration.newBuilder(query)
                .setUseLegacySql(false)
                .setNamedParameters(parameterValueMap)
                .build();
        queryJobConfiguration.getLabels();
        final long startTime = System.nanoTime();
        Job job = this.bigQuery.create(JobInfo.newBuilder(queryJobConfiguration).setJobId(jobId).build());
        // block
        job = job.waitFor();

        // you probably want to use some other metrics/gauges to understand actual performance.
        final long duration = (System.nanoTime() - startTime / 1000000);
        if( duration > logThresholdMs ) {
            val params = parameterValueMap
                    .entrySet()
                    .stream()
                    .map(e->String.format("%s=%s", e.getKey(), e.getValue().toString()))
                    .collect(Collectors.joining("|"));
            log.warn("slow query ran in {} ms: {}, params: [{}]", duration, query, params);
        }
        final List<T> result = new ArrayList<>();
        job.getQueryResults()
                .iterateAll()
                .forEach(fields->{
                    FieldValueList f = fields;
                    f.hasSchema();
                    T t = mapper.apply(fields);
                    result.add(t);
                });
        return result;
    }
}
