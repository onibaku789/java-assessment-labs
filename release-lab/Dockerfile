FROM eclipse-temurin:25.0.5.1_2-jdk-resolute AS build
ARG MAVEN_VERSION=3.10.2
ARG MAVEN_SHA512=57d2db6b37e7582119137ade544557bba741b29e62109ef81539a2bdfd6d0e641fdd5d8edb014af1816d0510deac6fac8f347433b3a1aa36316ba85aa77b8119
ADD https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz /tmp/maven.tar.gz
RUN echo "$MAVEN_SHA512  /tmp/maven.tar.gz" | sha512sum --check - \
    && tar -xzf /tmp/maven.tar.gz -C /opt \
    && rm /tmp/maven.tar.gz
ENV PATH="/opt/apache-maven-${MAVEN_VERSION}/bin:${PATH}"
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mvn -B -ntp package

FROM eclipse-temurin:25.0.5.1_2-jre-resolute
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/release-lab-1.0.0.jar /app/service.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
