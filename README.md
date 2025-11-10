# Custom NiFi Processors

This repository contains custom Apache Nifi processors. 

## Versioning

For convenience, we will tag releases according to version of the dependencies with Apache NiFi.
For example, if `org.apache.nifi:1.15.2` is used, the bundle should also be released with 
the same version (`1.15.2`).

## Processors

### ConvertJSONToSQL

It is a modified version of the original processor (v1.7.1). ([original source on github](https://github.com/apache/nifi/blob/rel/nifi-1.7.1/nifi-nar-bundles/nifi-standard-bundle/nifi-standard-processors/src/main/java/org/apache/nifi/processors/standard/ConvertJSONToSQL.java))
* Converted the date objects formatted with _EEE MMM dd HH:mm:ss zzz yyyy_ to _yyyy-MM-dd HH:mm:ss.SSS_ to 
prevent any string to date conversion failure.
* Added new tags.
* Added properties to control the catalog and schema name prepending to the table name.
* Enabled expression language support for _statement type_ property.
* Reads attribute "convertJSONToSQL.clearCache" from flowfile to clean the table meta info cache.
* Used null comparision operator in where statement.
* Removed the check of primary key meta info look up, so that it is always present.

### PushGaugeMetric

It pushes a gauge type metric to Prometheus Push Gateway.

## Build

To build bundle locally

```commandline
$ docker run --rm -it --network host --volume .:/work --volume ~/.m2:/root/.m2 --workdir /work --entrypoint=/bin/bash maven:3-openjdk-8
```

Install git

```commandline
$ apt update && apt install git -y
$ git config --global --add safe.directory /work
```

Build libraries

```commandline
$ mvn package
```

## Build non-interacive

Build libraries using local maven cache
```commandline
$ docker run --rm --network host --volume .:/work --volume ~/.m2:/root/.m2 --workdir /work --entrypoint=/bin/bash maven:3-openjdk-8 -c "apt update && apt install git -y && git config --global --add safe.directory /work && mvn package"
```

Run tests using local maven cache
```commandline
$ docker run --rm --network host --volume .:/work --volume ~/.m2:/root/.m2 --workdir /work --entrypoint=/bin/bash maven:3-openjdk-8 -c "apt update && apt install git -y && git config --global --add safe.directory /work && mvn test"
```

## Deployment

Docker compose file used in the production:
```yaml
version: "3"
services:
    # configuration manager for NiFi
    zookeeper:
        hostname: myzookeeper
        container_name: zookeeper_container_persistent
        image: 'bitnami/zookeeper:3.7.0'  # latest image as of 2022-01-03.
        logging:
            driver: "json-file"
            options:
                max-file: "100"
                max-size: "10m"
        restart: on-failure
        environment:
            - ALLOW_ANONYMOUS_LOGIN=yes
        networks:
            - my_persistent_network

    # version control for nifi flows
    registry:
        hostname: myregistry
        container_name: registry_container_persistent
        image: 'apache/nifi-registry:1.15.2'  # latest image as of 2022-01-03.
        logging:
            driver: "json-file"
            options:
                max-file: "100"
                max-size: "10m"
        restart: on-failure
        ports:
            - "18080:18080"
        environment:
            - LOG_LEVEL=INFO
            - NIFI_REGISTRY_DB_DIR=/opt/nifi-registry/nifi-registry-current/database
            - NIFI_REGISTRY_FLOW_PROVIDER=file
            - NIFI_REGISTRY_FLOW_STORAGE_DIR=/opt/nifi-registry/nifi-registry-current/flow_storage
        volumes:
            - ./nifi_registry/database:/opt/nifi-registry/nifi-registry-current/database
            - ./nifi_registry/flow_storage:/opt/nifi-registry/nifi-registry-current/flow_storage
        networks:
            - my_persistent_network

    # data extraction, transformation and load service
    nifi:
        hostname: nifi
        container_name: nifi_container_persistent
        image: 'apache/nifi:1.15.2'  # latest image as of 2022-01-03.
        logging:
            driver: "json-file"
            options:
                max-file: "100"
                max-size: "10m"
        restart: on-failure
        ports:
            - '8091:8080'
            #- '8443:8443'
        environment:
            - NIFI_WEB_HTTP_PORT=8080
            #- NIFI_WEB_HTTP_PORT=8443
            - NIFI_CLUSTER_IS_NODE=true
            - NIFI_CLUSTER_NODE_PROTOCOL_PORT=8082
            - NIFI_ZK_CONNECT_STRING=myzookeeper:2181
            - NIFI_ELECTION_MAX_WAIT=30 sec
            - NIFI_SENSITIVE_PROPS_KEY='<ADD_KEY_HERE>'
        #healthcheck:
            #test: "${DOCKER_HEALTHCHECK_TEST:-curl localhost:8091/nifi/}"
            #interval: "60s"
            #timeout: "55s"
            ##start_period: "5s"
            #retries: 5
        volumes:
            - ./nifi/database_repository:/opt/nifi/nifi-current/database_repository
            - ./nifi/flowfile_repository:/opt/nifi/nifi-current/flowfile_repository
            - ./nifi/content_repository:/opt/nifi/nifi-current/content_repository
            - ./nifi/provenance_repository:/opt/nifi/nifi-current/provenance_repository
            - ./nifi/state:/opt/nifi/nifi-current/state
            - ./nifi/logs:/opt/nifi/nifi-current/logs
            # uncomment the next line after copying the /conf directory from the container to your local directory to persist NiFi flows
            - ./nifi/conf:/opt/nifi/nifi-current/conf
            # NAV
            - "./dependencies/jdbc-driver/mssql/mssql-jdbc-7.4.1.jre8.jar:/opt/nifi/nifi-current/lib/mssql-jdbc-7.4.1.jre8.jar"
            # Shop (mysql)
            - "./dependencies/jdbc-driver/mysql/mysql-connector-java-8.0.18.jar:/opt/nifi/nifi-current/lib/mysql-connector-java-8.0.18.jar"
            # Bonsai
            - "./dependencies/jdbc-driver/oracle/ojdbc8.jar:/opt/nifi/nifi-current/lib/ojdbc8.jar"
            # WMS/lfs
            - "./dependencies/jdbc-driver/db2/jt400.jar:/opt/nifi/nifi-current/lib/jt400.jar"
            # self developed nifi processors
            - "./dependencies/nifi_flaconi_lib/nifi-flaconi-nar-1.15.2.nar:/opt/nifi/nifi-current/lib/nifi-flaconi-nar-1.15.2.nar"
            # key files
            - "./nifi/dependencies/key_files/nifi-drive-bundle-serviceaccount.p12:/opt/key_files/nifi-drive-bundle-serviceaccount.p12"
            # json transformer xml xslt
            - ./nifi/dependencies/xml:/opt/xml
        networks:
            - my_persistent_network


networks:
  my_persistent_network:
    driver: bridge
```

## Deployment
Docker compose file used in the production:
```yaml
version: "3"
services:
    # configuration manager for NiFi
    zookeeper:
        hostname: myzookeeper
        container_name: zookeeper_container_persistent
        image: 'bitnami/zookeeper:3.7.0'  # latest image as of 2022-01-03.
        logging:
            driver: "json-file"
            options:
                max-file: "100"
                max-size: "10m"
        restart: on-failure
        environment:
            - ALLOW_ANONYMOUS_LOGIN=yes
        networks:
            - my_persistent_network

    # version control for nifi flows
    registry:
        hostname: myregistry
        container_name: registry_container_persistent
        image: 'apache/nifi-registry:1.15.2'  # latest image as of 2022-01-03.
        logging:
            driver: "json-file"
            options:
                max-file: "100"
                max-size: "10m"
        restart: on-failure
        ports:
            - "18080:18080"
        environment:
            - LOG_LEVEL=INFO
            - NIFI_REGISTRY_DB_DIR=/opt/nifi-registry/nifi-registry-current/database
            - NIFI_REGISTRY_FLOW_PROVIDER=file
            - NIFI_REGISTRY_FLOW_STORAGE_DIR=/opt/nifi-registry/nifi-registry-current/flow_storage
        volumes:
            - ./nifi_registry/database:/opt/nifi-registry/nifi-registry-current/database
            - ./nifi_registry/flow_storage:/opt/nifi-registry/nifi-registry-current/flow_storage
        networks:
            - my_persistent_network

    # data extraction, transformation and load service
    nifi:
        hostname: nifi
        container_name: nifi_container_persistent
        image: 'apache/nifi:1.15.2'  # latest image as of 2022-01-03.
        logging:
            driver: "json-file"
            options:
                max-file: "100"
                max-size: "10m"
        restart: on-failure
        ports:
            - '8091:8080'
            #- '8443:8443'
        environment:
            - NIFI_WEB_HTTP_PORT=8080
            #- NIFI_WEB_HTTP_PORT=8443
            - NIFI_CLUSTER_IS_NODE=true
            - NIFI_CLUSTER_NODE_PROTOCOL_PORT=8082
            - NIFI_ZK_CONNECT_STRING=myzookeeper:2181
            - NIFI_ELECTION_MAX_WAIT=30 sec
            - NIFI_SENSITIVE_PROPS_KEY='fthdSDFSdfsFDGDftz655z5bze4ts'
        #healthcheck:
            #test: "${DOCKER_HEALTHCHECK_TEST:-curl localhost:8091/nifi/}"
            #interval: "60s"
            #timeout: "55s"
            ##start_period: "5s"
            #retries: 5
        volumes:
            - ./nifi/database_repository:/opt/nifi/nifi-current/database_repository
            - ./nifi/flowfile_repository:/opt/nifi/nifi-current/flowfile_repository
            - ./nifi/content_repository:/opt/nifi/nifi-current/content_repository
            - ./nifi/provenance_repository:/opt/nifi/nifi-current/provenance_repository
            - ./nifi/state:/opt/nifi/nifi-current/state
            - ./nifi/logs:/opt/nifi/nifi-current/logs
            # uncomment the next line after copying the /conf directory from the container to your local directory to persist NiFi flows
            - ./nifi/conf:/opt/nifi/nifi-current/conf
            # NAV
            - "./dependencies/jdbc-driver/mssql/mssql-jdbc-7.4.1.jre8.jar:/opt/nifi/nifi-current/lib/mssql-jdbc-7.4.1.jre8.jar"
            # Shop (mysql)
            - "./dependencies/jdbc-driver/mysql/mysql-connector-java-8.0.18.jar:/opt/nifi/nifi-current/lib/mysql-connector-java-8.0.18.jar"
            # Bonsai
            - "./dependencies/jdbc-driver/oracle/ojdbc8.jar:/opt/nifi/nifi-current/lib/ojdbc8.jar"
            # WMS/lfs
            - "./dependencies/jdbc-driver/db2/jt400.jar:/opt/nifi/nifi-current/lib/jt400.jar"
            # self developed nifi processors
            - "./dependencies/nifi_flaconi_lib/nifi-flaconi-nar-1.15.2.nar:/opt/nifi/nifi-current/lib/nifi-flaconi-nar-1.15.2.nar"
            # key files
            - "./nifi/dependencies/key_files/nifi-drive-bundle-serviceaccount.p12:/opt/key_files/nifi-drive-bundle-serviceaccount.p12"
            # json transformer xml xslt
            - ./nifi/dependencies/xml:/opt/xml
        networks:
            - my_persistent_network


networks:
  my_persistent_network:
    driver: bridge
```
