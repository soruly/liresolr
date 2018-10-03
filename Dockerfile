FROM openjdk:11
COPY . /liresolr
WORKDIR /liresolr
RUN ./gradlew distForSolr

FROM solr:7-alpine
COPY --chown=solr --from=0 /liresolr/dist/lire*.jar /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/
COPY --chown=solr --from=0 /liresolr/liresolr_conf /opt/solr/server/solr/configsets/liresolr_conf
