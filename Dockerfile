# syntax=docker/dockerfile:1

FROM solr:8.11.2

COPY --chown=solr:solr ["dist/lire.jar", "dist/liresolr.jar", "/opt/solr/server/solr-webapp/webapp/WEB-INF/lib/"]

COPY --chown=solr:solr ["conf/", "/opt/solr/server/solr/configsets/liresolr/conf/"]

CMD ["solr-foreground"]