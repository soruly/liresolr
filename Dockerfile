FROM openjdk:11
COPY . /liresolr
WORKDIR /liresolr
RUN ./gradlew distForSolr

FROM solr:7-alpine
COPY --chown=solr --from=0 /liresolr/dist/lire*.jar /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/
COPY --chown=solr --from=0 /liresolr/liresolr_conf /opt/solr/server/solr/configsets/liresolr_conf
USER root
RUN mkdir -p /var/solr && chown -R solr:solr /var/solr && chmod -R 777 /var/solr
USER solr
