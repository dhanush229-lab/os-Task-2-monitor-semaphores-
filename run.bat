@echo off
cd /d "%~dp0"
javac -d . src\SynchronizationServer.java
if errorlevel 1 pause & exit /b 1
java SynchronizationServer
pause
