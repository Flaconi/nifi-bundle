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
$ docker run --rm -it --network host --volume .:/work --workdir /work --entrypoint=/bin/bash maven:3-amazoncorretto-8-debian
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
