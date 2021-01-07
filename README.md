
# A Demo Of GraphQL-Java Over BigQuery

## Setup

On the google console: 

 1. Create a dataset named `demo_graphql_java` see [here](https://cloud.google.com/bigquery/docs/datasets).
 2. Run `create_tables.sql` using the BigQuery console. 

The following instructions are based on [this tutorial](https://cloud.google.com/community/tutorials/kotlin-springboot-compute-engine) if you have any issue look at that tutorial: 

We will use a bucket name that matches our project name:

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

Then use a GraphQL tool such as "GraphQL Playgournd" to look at:

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
