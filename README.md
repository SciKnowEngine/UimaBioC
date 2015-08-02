# UimaBioC

This project provides code that permits the use BioC structures as a UIMA type and provide support for it's use in text mining applications based on the CleartTk UIMA system. Note that our processing uses *either* BioC data formatted as XML or as JSON (and most of our applications use 

## The BioC Data Model

![UML Diagram](src/main/resources/uml/bioc.jpg)

This diagram shows the relationship between the various elements. Note that annotations are primarily structured using `infons` key-value tables, which are themselves unspecified. Using this library to extract data from `*.nxml` files generates annotations described in the following section: 

### Infons key-value pairs and data.



## Additional BioC Processing Libraries

* [Core Java Code Cloned from Sourceforge site](https://github.com/openbiocuration/BioC_Java)
* [Python library](https://github.com/2mh/PyBioC)

