#!/bin/sh

# Set the metadata server to the get projct id
PROJECTID=$(curl -s "http://metadata.google.internal/computeMetadata/v1/project/project-id" -H "Metadata-Flavor: Google")
BUCKET=$(curl -s "http://metadata.google.internal/computeMetadata/v1/instance/attributes/BUCKET" -H "Metadata-Flavor: Google")

echo "Project ID: ${PROJECTID} Bucket: ${BUCKET}"

# Get the files we need
gsutil cp gs://${BUCKET}/demo.jar .

# Install dependencies
apt-get update
apt-get -y --force-yes install default-jre
apt-get -y --force-yes install default-jdk

# Start server
java -jar demo.jar
