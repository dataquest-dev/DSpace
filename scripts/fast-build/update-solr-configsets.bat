call ..\envs\__basic.bat
rd /s /q %dspace_solr%server\solr\configsets\authority
rd /s /q %dspace_solr%server\solr\configsets\oai
rd /s /q %dspace_solr%server\solr\configsets\search
rd /s /q %dspace_solr%server\solr\configsets\statistics

:: Copy new Solr configuration
xcopy /e /h /i /q /y %dspace_application%solr\ %dspace_solr%server\solr\configsets\