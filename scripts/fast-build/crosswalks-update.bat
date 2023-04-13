call ..\envs\__basic.bat

call tomcat\stop.bat

xcopy /e /h /i /q /y %dspace_source%\dspace\config\crosswalks\oai\metadataFormats\ %dspace_application%\config\crosswalks\oai\metadataFormats\

cd %dspace_source%\scripts\fast-build\
