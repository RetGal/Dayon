# Dayon! 
[![Build Status](https://travis-ci.org/RetGal/Dayon.svg?branch=master)](https://travis-ci.org/RetGal/Dayon)
![Java CI (Maven)](https://github.com/RetGal/Dayon/workflows/Java%20CI%20(Maven)/badge.svg)
![Java CI (Ant)](https://github.com/RetGal/Dayon/workflows/Java%20CI%20(Ant)/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=RetGal_Dayon&metric=alert_status)](https://sonarcloud.io/dashboard?id=RetGal_Dayon)
[![dayon](https://snapcraft.io/dayon/badge.svg)](https://snapcraft.io/dayon)

Dayon! is an easy to use, cross-platform remote desktop assistance solution.

It consists of two parts - one for the assistant and one for the assisted. Both are included in one single package.
As quick launch versions for Windows, they are also available as individual binaries and as snap for various linux distributions.

## Key features

- easy setup (no router or network configuration needed on the assisted side)
- intuitive, multilingual user interface (de, en, es, fr, it, ru, tr, zh)
- assistant and assisted functionality in one package
- secure, encrypted communication (TLS)
- very low bandwidth usage
- cross-platform
- open source
- free (as hugs)

## History

Dayon! was originally developed by Marc Polizzi back in 2008.

With his consent, I have taken over the maintenance and further development of this software in late 2016.
I also moved the code base to GitHub, where it can easier be maintained.

## Releases

The ![latest version](https://github.com/RetGal/Dayon/releases) is v11.0 (Ballsy Beaver) - released more than ten years after the initial release.

This version comes with a new, access token based network configuration of the assisted.

The app is available directly from the <a href="https://www.microsoft.com/store/apps/9PBM5KW0C790">Microsoft Store</a>:

<a href='//www.microsoft.com/store/apps/9pbm5kw0c790?cid=storebadge&ocid=badge'><img src='https://developer.microsoft.com/store/badges/images/English_get_L.png' alt='English badge' width="127" height="52"/></a>

as snap:

[![Get it from the Snap Store](https://snapcraft.io/static/images/badges/en/snap-store-black.svg)](https://snapcraft.io/dayon)

as flatpak:

[Flathub](https://flathub.org/apps/details/io.github.retgal.Dayon)

or from [ppa:regal/dayon](https://launchpad.net/~regal/+archive/ubuntu/dayon)

## Website

[Deutsch](https://retgal.github.io/Dayon/de_index.html)<br>
[English](https://retgal.github.io/Dayon/index.html)<br>
[Français](https://retgal.github.io/Dayon/fr_index.html)<br>
[简体中文](https://retgal.github.io/Dayon/zh_index.html)<br>

Currently, there is no online documentation available for Italian, Spanish, Russian and Turkish.
Please refer to the [English](https://retgal.github.io/Dayon/index.html) or [French](https://retgal.github.io/Dayon/fr_index.html)
 version.
 
## Screen

![Assistant in action](/docs/dayon.screen.png?raw=true "Assistant connected")

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

For example translations for additional languages would make this app more useful for more earthlings - see: [src/main/resources/Babylon.properties](https://github.com/RetGal/Dayon/blob/master/src/main/resources/Babylon.properties) or [docs](https://github.com/RetGal/Dayon/tree/master/docs)

Also improvements of the existing translations and documentation are very welcome! For example, my current knowledge of Chinese is far below zero - and my knowledge of Russian or Turkish just fractions better..

Some additional testing, especially on macOS would also be highly appreciated.
