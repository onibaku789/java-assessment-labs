FROM eclipse-temurin:25.0.5.1_2-jdk-resolute AS build
WORKDIR /build
COPY mvnw .
COPY .mvn/wrapper .mvn/wrapper
COPY release-lab/pom.xml .
COPY release-lab/src src
RUN ./mvnw -B -ntp package

FROM eclipse-temurin:25.0.5.1_2-jre-resolute
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/release-lab-1.0.0.jar /app/service.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
