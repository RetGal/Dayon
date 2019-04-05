# Dayon! [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=RetGal_Dayon&metric=alert_status)](https://sonarcloud.io/dashboard?id=RetGal_Dayon)
Dayon! is an easy to use, cross-platform remote desktop assistance solution.

It consists of two parts - one for the assistant and one for the assisted. Both are included in one single package.

## Key features

- easy setup
- no router or network configuration needed on the assisted side
- friendly, trilingual user interface
- assistant and assisted functionality in same packet
- encrypted communication (TLS)
- cross-platform
- open source
- free (as in free beer)

## History

Dayon! was originally developed by Marc Polizzi back in 2008.

With his permission I moved the code base to GitHub, where it can easier be maintained.

## Releases

The ![latest version](https://github.com/RetGal/Dayon/releases) is v1.7.2 (v.1.7.3 for deb only)- released more than ten years after the initial release.

This version comes with a new connection protocol, allowing the clipboard to be shared between assisted and assistant.

It is most likely the very last version which is bundled with a Java 8 based JRE.

## Website

[Deutsch](https://retgal.github.io/Dayon/de_index.html)<br>
[English](https://retgal.github.io/Dayon/index.html)<br>
[Français](https://retgal.github.io/Dayon/fr_index.html)

## Screen

![Assistant connected](/docs/assistant_connected.jpg?raw=true "Assistant connected")

## Trivia

Dayon! means "Come in!" in Visayas - a local Philippine dialect. 

## Developers

The project can be built with Maven:

``mvn clean package``

or with Ant:

``ant clean build``

Have a look at ``pom.xml`` or ``build.xml`` for details and advanced options.
 
## Contributors
 
Any feedback and contributions are very welcome. 

You don't have to be a programmer!

For example translations for additional languages would make this app more useful for more earthlings - see: [src/main/java/mpo/dayon/common/babylon/Babylon.properties](https://github.com/RetGal/Dayon/blob/master/src/main/java/mpo/dayon/common/babylon/Babylon.properties) or [docs](https://github.com/RetGal/Dayon/tree/master/docs)

Some additional testing, especially on Mac OS would also be highly appreciated.

 
