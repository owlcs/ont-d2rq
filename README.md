# ONT-D2RQ – A Database to RDF Mapper
# This is a fork of D2RQ (https://github.com/d2rq/d2rq) adapted to our purposes.

There are following major differences with the original:

* It is a maven project.
* Up-to-date dependencies (java 1.8, jena 3.x, etc)
* Supporting OWL2 DL syntax
* No D2R Server
* Compatibility with ONT-API (OWL-API over Jena), see https://github.com/avicomp/ont-api 

## Note
Currently it is a kind of Demo. There is no jars-building in pom and no compiled version available in maven-central.

## Tests
For running tests you need to configure postgres and mysql databases. DB dumps are located in the doc/example folder.
 
## License 
Apache License Version 2.0

## Contacts
* sergei.zuev@avicomp.ru
* grigory.drobyazko@avicomp.ru