# ONT-D2RQ (ver 1.0.1) – A Database to RDF Mapper (API and Tools)
## This is a modified fork of D2RQ (https://github.com/d2rq/d2rq).

There are following major differences with the original:

* It is a maven project while original is ant
* Up-to-date dependencies (java 8, Apache Jena 3.x, etc)
* Supporting OWL2 DL syntax
* No D2R Server
* ONT-API (OWL-API over Jena, see https://github.com/avicomp/ont-api) in dependencies


## Usage
* to build command-line tools use `mvn clean package -Ptools`
* to run tools: `$ java -jar target\d2rq.jar`
* to include in dependencies [jitpack.io](https://jitpack.io/) can be used


## Tests
For running tests please configure postgres and mysql databases. 
DB dumps to prepare environment are located in the doc/example directory.
The file with tests db-settings: src/test/resources/db.properties
 
## License 
Apache License Version 2.0