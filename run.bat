@echo off
chcp 65001 >nul
cd /d "%~dp0"
call mvnw.cmd -B -ntp spring-boot:run %*
if errorlevel 1 exit /b %errorlevel%
