@echo off
setlocal
cd /d "%~dp0"

if not defined JAVA_HOME set "JAVA_HOME=D:\DevTools\Java\temurin-21"
if not defined MAVEN_HOME set "MAVEN_HOME=D:\DevTools\Maven\apache-maven-3.9.16\maven-mvnd-1.0.6-windows-amd64\mvn"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;D:\DevTools\Docker\resources\bin;%PATH%"

if not defined SPRING_DATASOURCE_URL set "SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/smart-campus?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
if not defined SPRING_DATASOURCE_USERNAME set "SPRING_DATASOURCE_USERNAME=root"
if not defined SPRING_DATASOURCE_PASSWORD set "SPRING_DATASOURCE_PASSWORD=123456"
if not defined NEO4J_ENABLED set "NEO4J_ENABLED=false"
if not defined SERVER_ADDRESS set "SERVER_ADDRESS=127.0.0.1"

call "%MAVEN_HOME%\bin\mvn.cmd" spring-boot:run
