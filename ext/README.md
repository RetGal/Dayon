# How to run a custom rendez-vous server (RVS)

## Set up the server

* put the [index.php](https://raw.githubusercontent.com/RetGal/Dayon/master/ext/index.php) file into a webservers directory which runs PHP with the SQLite extension
* either also put the empty [dayon.db](https://raw.githubusercontent.com/RetGal/Dayon/master/ext/dayon.db) database file into the same directory or let it be generated for you when the first token is being generated
* assure the user running the webserver has write access to the database file
* for security reasons, ensure that the database file can not be downloaded

## Set up the clients

In order your clients (eg. the assistant **and** the assisted) are going to use your own RVS,
you need to put an [assisted.yaml](https://raw.githubusercontent.com/RetGal/Dayon/master/docs/assisted.yaml) resp. [assistant.yaml](https://raw.githubusercontent.com/RetGal/Dayon/master/docs/assistant.yaml) file either:
* in the Dayon! home directory (.dayon)
* in the user home directory
* in the same directory as the .jar, resp. .exe file

These two files must both contain the **same** `tokenServerUrl`.
The URL can be any valid URL - for example:
`tokenServerUrl: "https://example.org/token"`
