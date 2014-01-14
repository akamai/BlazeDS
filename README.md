BlazeDS
=======

An alternate version of the [Adobe BlazeDS](http://sourceforge.net/adobe/blazeds/wiki/Home/) library that changes the XML format for Externalizable objects.  Rather than serializing them as base64-encoded binary content, instead the various `java.io.ObjectOutput` calls produce type-specific XML elements.
