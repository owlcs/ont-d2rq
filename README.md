# ONT-D2RQ – A Database to RDF Mapper
## This is a fork of D2RQ (https://github.com/d2rq/d2rq) adapted to our purposes.

There are following major differences with the original:

* It is a maven project while original is ant.
* Up-to-date dependencies (java 8, jena 3.x, etc)
* Supporting OWL2 DL syntax
* No D2R Server
* ONT-API (OWL-API over Jena, see https://github.com/avicomp/ont-api) in dependencies

## Note
Currently it is a kind of demo. There is no jars-building inside pom and no compiled version available in maven-central.

## Tests
For running tests please configure postgres and mysql databases. DB dumps are located in the doc/example folder.
 
## License 
Apache License Version 2.0

## Contacts
* sergei.zuev@avicomp.ru
* grigory.drobyazko@avicomp.ru