@ECHO OFF
SETLOCAL ENABLEDELAYEDEXPANSION

SET BASE_DIR=%~dp0
SET PROPERTIES_FILE=%BASE_DIR%\.mvn\wrapper\maven-wrapper.properties

IF NOT EXIST "%PROPERTIES_FILE%" (
  ECHO Missing %PROPERTIES_FILE%
  EXIT /B 1
)

FOR /F "tokens=1,* delims==" %%A IN (%PROPERTIES_FILE%) DO (
  IF "%%A"=="distributionUrl" SET DISTRIBUTION_URL=%%B
)

IF "%DISTRIBUTION_URL%"=="" (
  ECHO distributionUrl is not configured in %PROPERTIES_FILE%
  EXIT /B 1
)

FOR %%I IN ("%DISTRIBUTION_URL%") DO SET ARCHIVE_NAME=%%~nxI
SET WRAPPER_DIR=%BASE_DIR%\.mvn\wrapper
SET ARCHIVE_PATH=%WRAPPER_DIR%\%ARCHIVE_NAME%
SET DIST_DIR_NAME=%ARCHIVE_NAME:.tar.gz=%
SET DIST_DIR_NAME=%DIST_DIR_NAME:-bin=%
SET MVN_HOME=%WRAPPER_DIR%\%DIST_DIR_NAME%

IF NOT EXIST "%MVN_HOME%\bin\mvn.cmd" (
  IF NOT EXIST "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  IF NOT EXIST "%ARCHIVE_PATH%" powershell -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%ARCHIVE_PATH%'"
  IF EXIST "%MVN_HOME%" rmdir /S /Q "%MVN_HOME%"
  powershell -Command "tar -xzf '%ARCHIVE_PATH%' -C '%WRAPPER_DIR%'"
)

CALL "%MVN_HOME%\bin\mvn.cmd" %*
