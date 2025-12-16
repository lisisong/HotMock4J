
HotMock4J Java Agent
====================

A Java agent that can be packaged as a single JAR and configured on JVM startup to provide live, in-process mock data. It serves a built-in UI on `http://localhost:8080` for managing projects, mock plans, and class-level mocks without restarting the target app.

Requirements
------------
- JDK 17+
- Maven 3.8+ for building

Build the agent JAR
-------------------
```bash
mvn -pl hot-mock-4j-java17 -am package
```
This produces the shaded agent JAR at `hot-mock-4j-java17/target/hot-mock-4j-java17-1.0-SNAPSHOT.jar` with the proper manifest (`Premain-Class` and retransform flags).

Configure at JVM startup
------------------------
Add the agent JAR when launching your application:
```bash
java -javaagent:/path/to/hot-mock-4j-java17-1.0-SNAPSHOT.jar -jar your-app.jar
```
The agent starts before `main`, registers the transformers, and launches the embedded server on port 8080. Open `http://localhost:8080` to manage mock plans and class mocks in real time.
