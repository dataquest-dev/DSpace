@echo off
REM VSB Template Generation Script for DSpace 7 (Windows)
REM Generates faculty-specific submission forms from a base template

setlocal EnableDelayedExpansion

echo VSB Template Generation Script for DSpace 7
echo ==========================================

REM Set script directory
cd /d "%~dp0"

REM Create backup directory with timestamp
for /f "tokens=2 delims==" %%a in ('wmic OS Get localdatetime /value') do set "dt=%%a"
set "YY=%dt:~2,2%" & set "YYYY=%dt:~0,4%" & set "MM=%dt:~4,2%" & set "DD=%dt:~6,2%"
set "HH=%dt:~8,2%" & set "Min=%dt:~10,2%" & set "Sec=%dt:~12,2%"
set "BACKUP_DIR=bak-%YYYY%-%MM%-%DD%_%HH%-%Min%"

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

REM Validate template file
echo Validating template file...
if not exist "evyuka_form_template.xml" (
    echo Error: Template file evyuka_form_template.xml not found!
    pause
    exit /b 1
)

REM Create backup
echo Creating backup in %BACKUP_DIR%...
for %%f in (9270 FAST FBI FS FEI HGF FMT EKF USP AUD) do (
    if exist "evyuka_form_%%f.xml" copy "evyuka_form_%%f.xml" "%BACKUP_DIR%\" >nul
)

echo Generating faculty-specific forms...

REM Generate forms for all faculties except 9270
for %%f in (FAST FBI FS FEI HGF FMT EKF USP AUD) do (
    echo Generating evyuka_form_%%f.xml...
    copy "evyuka_form_template.xml" "evyuka_form_%%f.xml" >nul

    REM Replace PARAM placeholder with faculty code using PowerShell
    powershell -Command "(Get-Content 'evyuka_form_%%f.xml') -replace 'PARAM', '%%f' | Set-Content 'evyuka_form_%%f.xml'"

    if exist "evyuka_form_%%f.xml" (
        echo   Successfully generated evyuka_form_%%f.xml
    ) else (
        echo   Error generating evyuka_form_%%f.xml
    )
)

REM Special handling for faculty 9270
echo Generating evyuka_form_9270.xml (special handling)...
copy "evyuka_form_template.xml" "evyuka_form_9270.xml" >nul

REM Replace PARAM with 9270
powershell -Command "(Get-Content 'evyuka_form_9270.xml') -replace 'PARAM', '9270' | Set-Content 'evyuka_form_9270.xml'"

REM Remove discipline and programme fields using more precise PowerShell regex
powershell -Command "$content = Get-Content 'evyuka_form_9270.xml' -Raw; $content = $content -replace '(?s)<row>\s*<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>discipline</dc-element>.*?</field>\s*</row>', ''; $content = $content -replace '(?s)<row>\s*<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>programme</dc-element>.*?</field>\s*</row>', ''; $content | Set-Content 'evyuka_form_9270.xml'"

echo   Successfully generated evyuka_form_9270.xml

echo.
echo Form generation completed!
echo Backup created in: %BACKUP_DIR%
echo.
echo Next steps:
echo 1. Run fetch-vocabularies.bat to update controlled vocabularies
echo 2. Restart DSpace to load the new forms
echo 3. Test form functionality in the submission interface

pause
