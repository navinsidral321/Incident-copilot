@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
setlocal
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
set JAVA_EXE=java.exe
set JAVACMD=%JAVA_EXE%
if defined JAVA_HOME set JAVACMD=%JAVA_HOME%\bin\%JAVA_EXE%
@rem Execute Gradle
"%JAVACMD%" "-classpath" "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
:end
@rem End local scope
if "%ERRORLEVEL%"=="0" goto mainEnd
:fail
exit /b 1
:mainEnd
if "%ERRORLEVEL%"=="0" exit /b 0
exit /b 1
