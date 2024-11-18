<p align="center">
  <img src="docs/dayon.banner.png" width="100%"/>
</p>

![Java CI (Maven)](https://github.com/RetGal/Dayon/workflows/Java%20CI%20(Maven)/badge.svg)
![Java CI (Ant)](https://github.com/RetGal/Dayon/workflows/Java%20CI%20(Ant)/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=RetGal_Dayon&metric=alert_status)](https://sonarcloud.io/dashboard?id=RetGal_Dayon)
[![dayon](https://snapcraft.io/dayon/badge.svg)](https://snapcraft.io/dayon)

Dayon! is an easy-to-use, cross-platform remote desktop assistance solution.

It consists of two parts - one for the assistant and one for the assisted. Both are included in one single package.
As quick launch versions for Windows, they are also available as individual binaries, as snap, flatpak or nix for various linux distributions.

## Key features

- easy setup (no router or network configuration needed on the assisted side)
- intuitive, multilingual user interface (de, en, es, fr, it, ru, sv, tr, zh)
- assistant and assisted functionality in one package
- secure, end-to-end encrypted communication (TLS 1.3)
- very low bandwidth usage
- cross-platform
- open source
- free (as hugs)

## History

Dayon! was originally developed by Marc Polizzi back in 2008.

With his consent, I have taken over the maintenance and further development of this software in late 2016.
I also moved the code base to GitHub, where it can easier be maintained.

## Releases

The ![latest version](https://github.com/RetGal/Dayon/releases/latest) is v15.0.0 (Dolly Double) - released more than ten years after the initial release.

This is the first version in which the remote desktop can also be transmitted in colour - in addition to the bandwidth-saving greyscales. For a complete list of changes see: [Changelog](https://retgal.github.io/Dayon/download.html#change-log)

The app is available directly from the [Microsoft Store](https://apps.microsoft.com/detail/9PBM5KW0C790):

[<img src='https://developer.microsoft.com/store/badges/images/English_get_L.png' alt='English badge' width="127" height="52"/>](https://apps.microsoft.com/detail/9PBM5KW0C790)

as snap:

[![Get it from the Snap Store](https://snapcraft.io/static/images/badges/en/snap-store-black.svg)](https://snapcraft.io/dayon)

as flatpak:

[<img src="https://flathub.org/assets/badges/flathub-badge-en.svg" width="180"/>](https://flathub.org/apps/details/io.github.retgal.Dayon)

as nix:

[Nixhub](https://www.nixhub.io/packages/dayon)

or from [ppa:regal/dayon](https://launchpad.net/~regal/+archive/ubuntu/dayon)

## Website

[Deutsch](https://retgal.github.io/Dayon/de_index.html)<br>
[English](https://retgal.github.io/Dayon/index.html)<br>
[Français](https://retgal.github.io/Dayon/fr_index.html)<br>
[Swedish](https://retgal.github.io/Dayon/sv_index.html)<br>
[简体中文](https://retgal.github.io/Dayon/zh_index.html)<br>

Currently, there is no online documentation available for Italian, Spanish, Russian and Turkish.
Please refer to the [English](https://retgal.github.io/Dayon/index.html) or [French](https://retgal.github.io/Dayon/fr_index.html)
 version.
 
## Screen

![Assistant in action](/docs/dayon.screen.png?raw=true "Assistant connected")

## Connection establishment

![Connection establishment](/docs/dayon.connection.diagram.svg)

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

Also, improvements of the existing translations and documentation are very welcome! As my knowledge of Chinese is far below zero - and my knowledge of Russian or Turkish just fractions better..

Further testing, especially on macOS would also be highly appreciated.
