FROM eclipse-temurin:25.0.5.1_2-jre
ARG MODULE
WORKDIR /app
COPY --chown=10001:10001 ${MODULE}/target/${MODULE}-1.0.0.jar app.jar
USER 10001:10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
