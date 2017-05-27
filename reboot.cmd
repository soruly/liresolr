D:
cd D:\Java\Libs\solr-6.5.1
bin\solr stop -all | more
xcopy C:\Java\Projects\LireSolr\dist\lire.jar D:\Java\Libs\solr-6.5.1\server\solr-webapp\webapp\WEB-INF\lib /Y
xcopy C:\Java\Projects\LireSolr\dist\liresolr.jar D:\Java\Libs\solr-6.5.1\server\solr-webapp\webapp\WEB-INF\lib /Y
bin\solr start
cd C:\Java\Projects\LireSolr
