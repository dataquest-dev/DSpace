@echo off
REM VSB Vocabulary Fetching Script for DSpace 7 (Windows)
REM Downloads controlled vocabularies from VSB web services and converts them to DSpace format

setlocal EnableDelayedExpansion

echo VSB Vocabulary Fetching Script for DSpace 7
echo ===========================================

REM Set script directory
cd /d "%~dp0"

REM Configuration
set "VSB_BASE_URL=https://www.vsb.cz/edudocs"
set "VSB_TEST_URL=https://www-test.vsb.cz/edudocs"
set "TIMEOUT=30"

REM Create backup directory with timestamp
for /f "tokens=2 delims==" %%a in ('wmic OS Get localdatetime /value') do set "dt=%%a"
set "YY=%dt:~2,2%" & set "YYYY=%dt:~0,4%" & set "MM=%dt:~4,2%" & set "DD=%dt:~6,2%"
set "HH=%dt:~8,2%" & set "Min=%dt:~10,2%" & set "Sec=%dt:~12,2%"
set "BACKUP_DIR=vocab-backup-%YYYY%-%MM%-%DD%_%HH%-%Min%"

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

REM Check dependencies
echo Checking dependencies...
where curl >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: curl is required but not found!
    echo Please install curl and try again.
    pause
    exit /b 1
)

where xsltproc >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Warning: xsltproc not found. XSLT transformation will be skipped.
    set "XSLT_AVAILABLE=false"
) else (
    set "XSLT_AVAILABLE=true"
)

REM Check if XSLT file exists
if not exist "controlled-vocabulary2value-pairs.xsl" (
    echo Error: XSLT transformation file not found!
    echo Please ensure controlled-vocabulary2value-pairs.xsl exists in the current directory.
    pause
    exit /b 1
)

REM Create backup of existing vocabularies
echo Creating backup of existing vocabularies in %BACKUP_DIR%...
for %%t in (program branch subject subject-version) do (
    for %%f in (FAST FBI FS FEI HGF FMT EKF USP 9270) do (
        if exist "dir_%%t_%%f.xml" copy "dir_%%t_%%f.xml" "%BACKUP_DIR%\" >nul
        if exist "vp_%%t_%%f.xml" copy "vp_%%t_%%f.xml" "%BACKUP_DIR%\" >nul
    )
)

echo Downloading vocabularies from VSB web services...
echo.

REM Initialize counters
set /a failed_downloads=0
set /a failed_conversions=0
set /a total_downloads=0
set /a total_conversions=0

REM Download and process vocabularies
for %%t in (program branch subject subject-version) do (
    for %%f in (FAST FBI FS FEI HGF FMT EKF USP 9270) do (
        set /a total_downloads+=1

        echo Fetching %%t vocabulary for %%f...

        REM Try primary URL first
        curl -4s --connect-timeout %TIMEOUT% --max-time 60 "%VSB_BASE_URL%/%%t-directory?faculty=%%f" > "dir_%%t_%%f.xml.tmp" 2>nul

        REM Check if download was successful and contains valid data
        findstr /C:"<node" "dir_%%t_%%f.xml.tmp" >nul 2>&1
        if !ERRORLEVEL! equ 0 (
            move "dir_%%t_%%f.xml.tmp" "dir_%%t_%%f.xml" >nul
            echo   Success: Downloaded dir_%%t_%%f.xml

            REM Convert to DSpace format if XSLT is available
            if "!XSLT_AVAILABLE!"=="true" (
                set /a total_conversions+=1
                echo   Converting to value-pairs format...
                xsltproc --stringparam value_pairs_name "vp_%%t_%%f" --stringparam dc_term "programme" controlled-vocabulary2value-pairs.xsl "dir_%%t_%%f.xml" > "vp_%%t_%%f.xml" 2>nul

                if exist "vp_%%t_%%f.xml" (
                    echo   Success: Generated vp_%%t_%%f.xml
                ) else (
                    echo   Warning: Conversion failed for vp_%%t_%%f.xml
                    set /a failed_conversions+=1
                )
            )
        ) else (
            REM Try fallback URL
            echo   Primary URL failed, trying fallback...
            curl -4s --connect-timeout %TIMEOUT% --max-time 60 "%VSB_TEST_URL%/%%t-directory?faculty=%%f" > "dir_%%t_%%f.xml.tmp" 2>nul

            findstr /C:"<node" "dir_%%t_%%f.xml.tmp" >nul 2>&1
            if !ERRORLEVEL! equ 0 (
                move "dir_%%t_%%f.xml.tmp" "dir_%%t_%%f.xml" >nul
                echo   Success: Downloaded dir_%%t_%%f.xml (fallback)

                REM Convert to DSpace format if XSLT is available
                if "!XSLT_AVAILABLE!"=="true" (
                    set /a total_conversions+=1
                    echo   Converting to value-pairs format...
                    xsltproc --stringparam value_pairs_name "vp_%%t_%%f" --stringparam dc_term "programme" controlled-vocabulary2value-pairs.xsl "dir_%%t_%%f.xml" > "vp_%%t_%%f.xml" 2>nul

                    if exist "vp_%%t_%%f.xml" (
                        echo   Success: Generated vp_%%t_%%f.xml
                    ) else (
                        echo   Warning: Conversion failed for vp_%%t_%%f.xml
                        set /a failed_conversions+=1
                    )
                )
            ) else (
                echo   Failed: Could not download dir_%%t_%%f.xml
                set /a failed_downloads+=1

                REM Try to restore from backup
                if exist "%BACKUP_DIR%\dir_%%t_%%f.xml" (
                    copy "%BACKUP_DIR%\dir_%%t_%%f.xml" . >nul
                    echo   Restored from backup: dir_%%t_%%f.xml

                    if "!XSLT_AVAILABLE!"=="true" (
                        if exist "%BACKUP_DIR%\vp_%%t_%%f.xml" (
                            copy "%BACKUP_DIR%\vp_%%t_%%f.xml" . >nul
                            echo   Restored from backup: vp_%%t_%%f.xml
                        )
                    )
                )

                REM Clean up temp file
                if exist "dir_%%t_%%f.xml.tmp" del "dir_%%t_%%f.xml.tmp" >nul
            )
        )

        echo.
    )
)

echo Summary:
echo ========
echo Total vocabulary downloads attempted: %total_downloads%
set /a successful_downloads=%total_downloads%-%failed_downloads%
echo Successful downloads: %successful_downloads%
echo Failed downloads: %failed_downloads%

if "!XSLT_AVAILABLE!"=="true" (
    set /a successful_conversions=%total_conversions%-%failed_conversions%
    echo Successful conversions: !successful_conversions!
    echo Failed conversions: %failed_conversions%
)

echo.
echo Backup created in: %BACKUP_DIR%

if %failed_downloads% gtr 0 (
    echo.
    echo Warning: Some vocabularies could not be updated.
    echo Check the backup directory and restore manually if needed.
) else (
    echo.
    echo All vocabularies updated successfully!
)

echo.
echo Next steps:
echo 1. Regenerate forms with evyuka_form_template.bat if needed
echo 2. Restart DSpace to load the new vocabularies
echo 3. Test vocabulary functionality in the submission interface

pause
