# Featured-to-GeoServer

Open-source GeoServer loading software for Featured, developed by Publieke Dienstverlening op de Kaart (PDOK).

Featured-to-GeoServer is one of the output applications in the PDOK ETL landscape.
It fills a PostGIS database with geodata that is used to provide WMS and WFS services to end users.
The input is a list of changelog files generated by Featured (<https://github.com/PDOK/featured>).

## Usage

Featured-to-GeoServer supports a server mode and a command-line mode.
To run it locally, the following command can be used to start it in server mode:

    $ lein ring server-headless

To use the command line locally, use:

    $ lein run [args]

or

    $ lein uberjar
    $ java -jar featured-to-geoserver-0.9.8-standalone.jar [args]

## Options

Featured-to-Extracts supports the following environment variables in server mode:

| Variable | Description |
|---|---|
| -Ddatabase_url="//localhost:5432/pdok" | |
| -Ddatabase_user="postgres" | |
| -Ddatabase_password="postgres" | |
| -Dn_workers=5 | Number of parallel workers. |
| -Dqueue_length=20 | Number of jobs that can be queued for processing. |

On the command line, the following parameters can be used:

| Parameter | Description |
|---|---|
| -f / --format FORMAT | File format (zip or plain), defaults to "zip". |
| --db-host HOST | Database host, defaults to "localhost". |
| --db-port PORT | Database port, defaults to 5432. |
| --db-user USER | Database user, defaults to "postgres". |
| --db-password PASSWORD | Database password, defaults to "postgres". |
| --db-name DATABASE | Database name, defaults to "pdok". |
| --array COLLECTION/FIELD | Specify array mapping for field, defaults to {}. |
| --unnest COLLECTION/FIELD | Specify unnesting on field, defaults to {}. |
| --exclude-filter FIELD=EXCLUDE-VALUE | Exclude changelog entries, defaults to {}. |

## License

Copyright © 2017 Publieke Dienstverlening op de Kaart

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
