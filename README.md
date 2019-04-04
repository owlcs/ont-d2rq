# ONT-D2RQ (ver 1.0.5) – A Database to OWL Mapper (API and Tools)
## This is a modified fork of D2RQ (https://github.com/d2rq/d2rq).

There are following major differences with the original:

* It is a maven project while the original is ant
* Up-to-date dependencies (java 8, Apache Jena 3.x, etc)
* Supporting OWL2 DL syntax
* A Fuseki (SPARQLer) based embedded server instead of Joseki based native D2RQ Server
* [ONT-API](https://github.com/avicomp/ont-api) ([OWL-API](https://github.com/owlcs/owlapi) over [Apache Jena](https://github.com/apache/jena)) in dependencies

## Usage
* To build: `mvn clean package`
* To run tools: `$ java -jar tools\target\d2rq.jar`
* To include in dependencies [jitpack.io](https://jitpack.io/) can be used

## Notes and propositions
* For ONT-API integration there is a special kind of `OWLDocumentSource`: `ru.avicomp.d2rq.D2RQGraphDocumentSource` 
* It is also assumed that `ru.avicomp.d2rq.MappingFilter` can used to filter the default database schema
* To transform DB data into a more suitable form [ONT-MAP](https://github.com/avicomp/ont-map) can be used

## Tests
For running tests please configure postgres and mysql databases. 
DB sql-dumps to prepare environment are located in the doc/example directory.
The file with tests db-settings: `api/src/test/resources/db.properties`
 
## License 
Apache License Version 2.0