call ..\envs\__basic.bat

set tomcat_bin=%tomcat%\bin\

call tomcat\stop.bat

cd %dspace_source%\scripts\fast-build\

xcopy /e /h /i /q /y %dspace_source%\dspace\config\crosswalks\oai\metadataFormats\ %dspace_application%\config\crosswalks\oai\metadataFormats\

cd %dspace_source%\scripts\fast-build\
