
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

You can run GraphQL Playbround to this out which looks like this: 

![GraphQL Playground](https://raw.githubusercontent.com/simbo1905/bigquery-graphql/master/graphql-bigquery.png)

The GraphQL scheme is:

```graphql
type Query {
    bookById(id: ID): Book
}

type Book {
    id: ID
    name: String
    pageCount: Int
    author: Author
}

type Author {
    id: ID
    firstName: String
    lastName: String
}
```

The matching BigQuery schema is:

```sql
create table demo_graphql_java.book ( id string, name string, pageCount string, authorId string ); 
create table demo_graphql_java.author ( id string, firstName string, lastName string );  
```

This codebase uses a generic file to `wirings.json` to map GraphQL onto BigQuery SQL. If we look in that file we have:

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

 1. There is a field on `Query` called `bookById` which fines our top level query:
    * The graphql source parameter/attribute is `id` as we can query by e.g., `bookById(id:"book-1")`
    * The sql query named parameter is also `id` as that is the identity column on the book table. 
    * The sql query is a simple select-by-id that uses the sql param i.e., `where id=@id`
    * The list of fields returned by the query is named in `mapperCsv`. We have to pass this as BigQuery won't tell us this fact unlike a standard JDBC ResultSet :cry:.
 2. There is a field on `Book` called `author` which requires querying the author table based on the `authorId` of the book:
    * The graphql source parameter/attribute is `authorId` as this is the name of the attribute on the `Book` entity as loaded from BigQuery.
    * The sql query named parameter is `id` as that is also the column name of the identity column on the author table.
    * The sql query is also a simple select-by-id using `where id=@id`
    * Once again the list of the fields returned by the query is supplied as BigQuery doesn't provide that :cry:.

## TODO Development

At the moment the code assumes that all SQL query parameters are strings. 
It is left as an exercise to the reader to upgrade the code to deal with other types. 

## BigQuery Setup

On the Google Cloud console: 

 1. Create a dataset named `demo_graphql_java` see [here](https://cloud.google.com/bigquery/docs/datasets).
 2. Run `create_tables.sql` using the BigQuery console. 

## Running Under Docker

First create the tables in a dataset `demo_graphql_java` as described above.

Create a service account `bigquery-graphql` then grant it the bigquery user role:

```sh
gcloud projects add-iam-policy-binding ${YOUR_PROJECT} \
  --member='serviceAccount:bigquery-graphql@${YOUR_PROJECT}.iam.gserviceaccount.com' \
  --role='roles/bigquery.user'
```

Grant the service account read to the two tables using these instructions [bigquery/docs/table-access-controls](https://cloud.google.com/bigquery/docs/table-access-controls#bq). 

In *my* case I did something lik this. YMMV you need to change the identifiers to match your project/sa:

```sh
$ cat policy.json
{
"bindings": [
 {
   "members": [
     "serviceAccount:bigquery-graphql@capable-conduit-300818.iam.gserviceaccount.com"
   ],
   "role": "roles/bigquery.dataViewer"
 }
]
}
$ bq set-iam-policy capable-conduit-300818:demo_graphql_java.author policy.json
$ bq set-iam-policy capable-conduit-300818:demo_graphql_java.author policy.json
```

On the console create a JSON keyfile for the service account and save it in the current directory. 
Save the file name as "bigquery-sa.json". Then run Docker passing in access to that keyfile: 

```sh
docker run -it \
  --volume $(pwd):/home/project \
  -e GOOGLE_APPLICATION_CREDENTIALS=/home/project/bigquery-sa.json \
  -p 8080:8080 simonmassey/bigquery-graphql:latest
```

## Run In Cloud On VM

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

Then use a GraphQL tool such as "GraphQL Playground" to look query:

```sh
http://$EXTERNAL_IP:8080/graphql
```

## Docker Release Via GitHub Actions

Is done as per https://gist.github.com/faph/20331648cdc0b492eba0f4d83f69eaa5

## Run On KNative

Do the first helloworld deployment from the video [Serverless with Knative - Mete Atamel](https://www.youtube.com/watch?v=HiIJqMqFbC0).
**Note** The latest setup material is at [https://github.com/meteatamel/knative-tutorial/tree/master/setup](https://github.com/meteatamel/knative-tutorial/tree/master/setup) and you *only* need to setup Knative Serving and not anything else. 

We need to create a secret containing your service account token. 

Using [these instructions](https://knative.dev/docs/serving/samples/secrets-go/) I found that this worked:

```sh
kubectl create secret generic graphql-bigquery --from-file=bigquery-sa.json
```

The secret is referenced in service-v1.yaml which is the knative service you can install with: 

```sh
kubectl apply -f service-v1.yaml
```

## Manage KNative via Helm

Grab the latest helm and put it on your path. Then install the KNative service with: 

```sh
helm install bigquery-graphql ./bigquery-graphql
```

Better yet use [github pages](https://docs.github.com/en/free-pro-team@latest/github/working-with-github-pages/creating-a-github-pages-site) and create a chart repo: 

```sh
helm package bigquery-graphql
mv bigquery-graphql-1.0.4.tgz charts/
helm repo index charts --url https://${YOUR_ORG}.github.io/bigquery-graphql/charts
git add charts/*
git commit -am 'charts update'
git pull && git push
```

Now you can use the declarative helmfile.yaml to update all the services with: 

```sh
helmfile sync
```
