# Instana Agent team - Coding Assignment

This is a simple project for pair-programming during a coding assignment.

After requirements are met, it should be possible to compile and run the (integration) tests. The code will be evolved
during the pairing session.


## Requirements

The following requirements need to be met (installed) for the project to compile and run tests:

- Java 8+
- Maven 3+
- Docker (Please see the TestContainers [Docker Requirements](https://www.testcontainers.org/supported_docker_environment/) )
- Any IDE of choice


## Building & Running Tests

To build and execute tests run:

```
mvn clean verify
```

When running tests from the IDE, please make sure to first execute `mvn package` so that the fat-jar will be (re-)build
before it gets mounted inside the test container.

