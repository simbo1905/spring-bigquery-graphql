package com.github.simbo1905.bigquerygraphql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.Resources;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@Component
@Slf4j
public class BigQueryDataFetchers {

    @Autowired
    BigQueryRunner bigQueryRunner;

    @Value("${wirings.json}")
    String wirings;

    /**
     * Logic to look for <pre>@cache(ms: 15000)</pre> directives on fields. It then creates a Guava TTL cache
     * and wraps the DataFetcher invocation in cache get/put logic.
     */
    class CachedDirective implements SchemaDirectiveWiring {
        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            GraphQLFieldDefinition field = environment.getElement();
            val directive = field.getDirective("cache");
            if( directive != null) {
                // create a TTL cache with the timeout set via the directive
                int ms = (int) directive.getArgument("ms").getValue();
                final Cache<String, Map<String,String>> cache = CacheBuilder.newBuilder()
                        .expireAfterWrite(ms, TimeUnit.MILLISECONDS)
                        .build();

                // get the original fetcher
                GraphQLFieldsContainer parentType = environment.getFieldsContainer();
                final DataFetcher uncachedDataFetcher = environment.getCodeRegistry().getDataFetcher(parentType, field);

                // return a wrapper that uses the cache for this field
                DataFetcher cachedDataFetcher = (e) -> {
                    val id = Objects.toString(e.getArgument("id"));
                    var value = cache.getIfPresent(Objects.toString(id));
                    if( value == null ){
                        value = (Map<String, String>) uncachedDataFetcher.get(e);
                        if( value != null )
                            cache.put(id, value);
                    }
                    return value;
                };

                // export the wrapper to the framework
                FieldCoordinates coordinates = FieldCoordinates.coordinates(parentType, field);
                environment.getCodeRegistry().dataFetcher(coordinates, cachedDataFetcher);
            }

            return field;
        }
    }

    @SneakyThrows
    public RuntimeWiring wire(RuntimeWiring.Builder wiring) {
        // apply the caching directive
        wiring = wiring.directiveWiring(new CachedDirective());

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
