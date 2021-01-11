package com.github.simbo1905.bigquerygraphql;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.io.IOException;
import java.net.URL;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * See https://www.graphql-java.com/tutorials/getting-started-with-spring-boot/
 */
@Component
public class GraphQLProvider {

    private GraphQL graphQL;

    @Bean
    public GraphQL graphQL() {
        return graphQL;
    }

    @Autowired
    BigQueryDataFetchers bigQueryDataFetchers;

    @PostConstruct
    public void init() throws IOException {
        URL url = Resources.getResource("schema.graphqls");
        String sdl = Resources.toString(url, Charsets.UTF_8);
        GraphQLSchema graphQLSchema = buildSchema(sdl);
        this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    }

    private GraphQLSchema buildSchema(String sdl) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("bookById",
                                bigQueryDataFetchers.queryForOne(
                                        bigQueryDataFetchers.bookByIdQuery,
                                        bigQueryDataFetchers.bookByIdMapperCsv,
                                        "id",
                                        "id")))
                .type(newTypeWiring("Book")
                        .dataFetcher("author",
                                bigQueryDataFetchers.queryForOne(
                                        bigQueryDataFetchers.authorByIdQuery,
                                        bigQueryDataFetchers.authorByIdMapperCsv,
                                        "authorId",
                                        "id")))
                .build();
    }
}
