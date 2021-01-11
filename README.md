
# A Demo Of GraphQL-Java Over BigQuery

This codebase is based on the tutorial [getting-started-with-spring-boot](https://www.graphql-java.com/tutorials/getting-started-with-spring-boot/).

Exactly like the original tutorial if we query with:

```graphql
{
  bookById(id:"book-1"){
    id
    name
    pageCount
    author {
      firstName
      lastName
    }
  }
}
```

Then we get back: 

```json
{
  "data": {
    "book1": {
      "id": "book-1",
      "name": "Harry Potter and the Philosopher's Stone",
      "pageCount": 223,
      "author": {
        "firstName": "Joanne",
        "lastName": "Rowling"
      }
    }
  }
}
```

This looks like: 

![graphQL Playground](https://raw.githubusercontent.com/simbo1905/bigquery-graphql/master/graphql-bigquery.png)

The codebase uses a generic file to `wirings.json` to map GraphQL onto BigQuery SQL. If we look in that file we have:

```json
[
  {
    "typeName": "Query",
    "fieldName": "bookById",
    "sql":"select id,name,pageCount,authorId from demo_graphql_java.book where id=@id",
    "mapperCsv":"id,name,pageCount,authorId",
    "gqlAttr": "id",
    "sqlParam": "id"
  },
  {
    "typeName": "Book",
    "fieldName": "author",
    "sql":"select id,firstName,lastName from demo_graphql_java.author where id=@id",
    "mapperCsv":"id,firstName,lastName",
    "gqlAttr": "authorId",
    "sqlParam": "id"
  }
]
```

That contains two wiring: 

 1. There is a field on `Query` called `bookById`:
    * The graphql source parameter/attribute is `id` as we query as `bookById(id:"book-1")`
    * The sql query named parameter is also `id` as that is the identity column on the book table. 
    * The sql query is a simple select-by-id.
    * The list of fields returned by the query is named in `mapperCsv` as BigQuery won't tell us this fact.   
 2. There is a field on `Book` called `author`:
    * The graphql source parameter/attribute is `authorId` as this is the name of the attribute on the `Book` entity.
    * The sql query named parameter is `id` as that is also the identity column on the author table. 
    * The sql query is allso a simple select-by-id.
    * Once again the list of the fields returned by the query is supplied as BigQuery doesn't provide that. 

## TODO

At the moment the code assumes that all SQL query parameters are strings. 
It is left as an exercise to the reader to upgrade the code to deal with other types. 

## Setup

On the Google Cloud console: 

 1. Create a dataset named `demo_graphql_java` see [here](https://cloud.google.com/bigquery/docs/datasets).
 2. Run `create_tables.sql` using the BigQuery console. 

The following instructions are based on [this tutorial](https://cloud.google.com/community/tutorials/kotlin-springboot-compute-engine) if you have any issue look at that tutorial: 

Use your own project name not mine here. I used a bucket name that matches the project name:

```sh
export YOUR_PROJECT=capable-conduit-300818
export BUCKET=capable-conduit-300818
```

Upload the jar using the `gcloud` SDK:

 1. login if not already logged in: `gcloud auth login $YOUR_LOGIN`
 2. change to your project if not already: `gcloud config set project $YOUR_PROJECT`
 3. create a bucket the same name as your project `gsutil mb gs://$YOUR_PROJECT`
 4. build the code `mvn clean package`
 5. rename the jar `cp $(find target -name \*.jar) target/demo.jar`
 6. upload the jar `gsutil cp target/demo.jar gs://$YOUR_PROJECT/`

Now create an instance that will run the code in the bucket:

```sh
gcloud compute instances create demo-instance \
    --image-family debian-10 \
    --image-project debian-cloud \
    --machine-type f1-micro \
    --scopes "userinfo-email,cloud-platform" \
    --metadata-from-file startup-script=instance-startup.sh \
    --metadata BUCKET=${BUCKET} \
    --zone us-east1-b \
    --tags http-server
```

Now create firewall rule: 

```sh
gcloud compute firewall-rules create default-allow-http-8080 \
    --allow tcp:8080 \
    --source-ranges 0.0.0.0/0 \
    --target-tags http-server \
    --description "Allow port 8080 access to http-server"
```

Next get the external IP: 

```sh
gcloud compute instances list
```

Then use a GraphQL tool such as "GraphQL Playground" to look at:

```sh
http://$EXTERNAL_IP:8080/graphql
```

You can run a GraphQL query such as:

```
# Write your query or mutation here
{
  bookById(id:"book-1"){
    id
    name
    pageCount
    author {
      firstName
      lastName
    }
  }
}
```
