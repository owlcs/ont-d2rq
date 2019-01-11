# ONT-D2RQ (ver 1.0.4) – A Database to OWL Mapper (API and Tools)
## This is a modified fork of D2RQ (https://github.com/d2rq/d2rq).

There are following major differences with the original:

* It is a maven project while the original is ant
* Up-to-date dependencies (java 8, Apache Jena 3.x, etc)
* Supporting OWL2 DL syntax
* A Fuseki based D2R Server (which, at the moment, is inferior in functionality to the original, Joseki based, server)
* [ONT-API](https://github.com/avicomp/ont-api) ([OWL-API](https://github.com/owlcs/owlapi) over [Apache Jena](https://github.com/apache/jena)) in dependencies


## Usage
* to build command-line tools use `mvn clean package`
* to run tools: `$ java -jar tools\target\d2rq.jar`
* to include in dependencies [jitpack.io](https://jitpack.io/) can be used

## Notes and propositions
* For integration within ONT-API there is a special kind of `OWLDocumentSource`: `ru.avicomp.d2rq.D2RQGraphDocumentSource` 
* It is assumed that `ru.avicomp.d2rq.MappingFilter` can used to filter the default database schema
* to transform db data into a more suitable form [ONT-MAP](https://github.com/avicomp/ont-map) can be used


## Tests
For running tests please configure postgres and mysql databases. 
DB sql-dumps to prepare environment are located in the doc/example directory.
The file with tests db-settings: `api/src/test/resources/db.properties`
 
## License 
Apache License Version 2.0