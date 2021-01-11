package com.github.simbo1905.bigquerygraphql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.schema.idl.RuntimeWiring;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@Component
@Slf4j
public class BigQueryDataFetchers {

    @Autowired
    BigQueryRunner bigQueryRunner;

    @Value("${wirings.json}")
    String wirings;

    @SneakyThrows
    public RuntimeWiring wire(RuntimeWiring.Builder wiring) {
        log.info("Loading wirings file: "+ wirings);
        URL url = Resources.getResource(wirings);
        if( url == null ){
            throw new IllegalStateException("could not resolve "+ wirings +" from resources");
        }

        String jsonCarArray = Resources.toString(url, Charsets.UTF_8);
        ObjectMapper objectMapper = new ObjectMapper();
        List<QueryAndMapperCsv> mappings = objectMapper.readValue(jsonCarArray, new TypeReference<>() {});

        for (QueryAndMapperCsv queryAndMapperCsv : mappings) {
            log.info("wiring: {}", queryAndMapperCsv.toString());
            wiring = wiring
                    .type(newTypeWiring(queryAndMapperCsv.typeName)
                            .dataFetcher(queryAndMapperCsv.fieldName,
                                    bigQueryRunner.queryForOne(
                                            queryAndMapperCsv.sql,
                                            queryAndMapperCsv.mapperCsv,
                                            queryAndMapperCsv.gqlAttr,
                                            queryAndMapperCsv.sqlParam)));
        }

        return wiring.build();
    }

    @Getter @Setter @ToString
    static class QueryAndMapperCsv {
        /**
         * Wiring typeName e.g. "Query", "Book"
         */

        String typeName;
        /**
         * Wiring fieldName e.g. "bookById", "author"
         */

        String fieldName;
        /**
         * The BigQuery query sql.
         */
        String sql;

        /**
         * The result set colums and BQ unfortunately does not supply this query result meta-data
         */
        String mapperCsv;

        /**
         * The source parameter (if query) or attribute (if entity) on the GraphQL side e.g., "authorId"
         */
        String gqlAttr;

        /**
         * The destination attribute on the SQL side e.g., "id"
         */
        String sqlParam;
    }

}
