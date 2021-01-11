package com.github.simbo1905.bigquerygraphql;

import graphql.schema.idl.RuntimeWiring;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@Component
@Slf4j
public class BigQueryDataFetchers {
    @Value("${query.bookById}")
    String bookByIdQuery;

    @Value("${query.authorById}")
    String authorByIdQuery;

    @Value("${mapper.bookByIdCsv}")
    String bookByIdMapperCsv;

    @Value("${mapper.authorByIdCsv}")
    String authorByIdMapperCsv;

    @Autowired
    BigQueryRunner bigQueryRunner;

    @PostConstruct
    public void logConfig() {
        log.info("bookByIdQuery: {}", bookByIdQuery);
        log.info("authorByIdQuery: {}", authorByIdQuery);
        log.info("bookByIdMapperCsv: {}", bookByIdMapperCsv);
        log.info("authorByIdMapperCsv: {}", authorByIdMapperCsv);
    }

    public RuntimeWiring wire(RuntimeWiring.Builder wiring) {
        return wiring
                .type(newTypeWiring("Query")
                        .dataFetcher("bookById",
                                bigQueryRunner.queryForOne(
                                        bookByIdQuery,
                                        bookByIdMapperCsv,
                                        "id",
                                        "id")))
                .type(newTypeWiring("Book")
                        .dataFetcher("author",
                                bigQueryRunner.queryForOne(
                                        authorByIdQuery,
                                        authorByIdMapperCsv,
                                        "authorId",
                                        "id")))
                .build();
    }
}
