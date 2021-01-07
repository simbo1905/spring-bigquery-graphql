#!/bin/bash
set +x
mvn clean package && cp $(find target -name \*.jar) target/demo.jar && gsutil cp target/demo.jar gs://$YOUR_PROJECT/
