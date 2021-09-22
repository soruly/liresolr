# liresolr

[![License](https://img.shields.io/github/license/soruly/liresolr.svg?style=flat-square)](https://github.com/soruly/liresolr/blob/master/LICENSE)
[![GitHub Workflow Status](https://img.shields.io/github/workflow/status/soruly/liresolr/Docker%20Image%20CI?style=flat-square)](https://github.com/soruly/liresolr/actions)
[![Docker](https://img.shields.io/docker/pulls/soruly/liresolr?style=flat-square)](https://hub.docker.com/r/soruly/liresolr)
[![Docker Image Size](https://img.shields.io/docker/image-size/soruly/liresolr/latest?style=flat-square)](https://hub.docker.com/r/soruly/liresolr)
[![Discord](https://img.shields.io/discord/437578425767559188.svg?style=flat-square)](https://discord.gg/K9jn6Kj)

Apache Solr with LIRE built-in

## Getting started

### Create Solr cores

Create folders for solr cores first. The folder must be owned by uid and gid 8983

```bash
mkdir -p /var/mycores
sudo chown 8983:8983 /var/mycores
```

Run the docker. This would create solr core and start the server.

```bash
docker run -d -p 8983:8983 \
  -v /var/mycores:/opt/solr/server/solr/mycores \
   ghcr.io/soruly/liresolr:latest solr-precreate mycore
```

Head over to http://127.0.0.1:8983 and you should see your cores created and loaded

Note: DO NOT use the web UI to create solr cores.

### Adding files for indexing

Assume you have xml files hashed by of LIRE, load the hash like this:

```bash
curl -X POST -H "Content-Type: text/xml" -d @hash.xml "http://127.0.0.1:8983/solr/mycore/update?wt=json&commit=true"
```

### Submit files for searching

```bash
curl -X POST -H "Content-type: image/jpeg" --data-binary @image.jpg "http://127.0.0.1:8983/solr/mycore/lireq?field=cl_ha&ms=false&accuracy=100&candidates=1000000&rows=30"
```
