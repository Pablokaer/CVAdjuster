# Multi-stage Dockerfile: build with Maven, run on JRE
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build
# Set Maven JVM memory to avoid OOM during build
ENV MAVEN_OPTS="-Xmx1024m -Xms256m"
# Ensure the container uses UTF-8 locale so resource filtering won't fail on non-ASCII
ENV LANG=C.UTF-8
# Copy only pom first to leverage Docker layer caching for dependencies
COPY pom.xml ./
# Pre-resolve dependencies to fail fast and cache downloads
RUN mvn -B -DskipTests dependency:resolve

# Copy source and run the package phase
COPY src ./src
# Force Maven to use UTF-8 when filtering resources (defensive: also configured in pom.xml)
RUN mvn -B -DskipTests -Dproject.build.sourceEncoding=UTF-8 package

FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy fat jar produced by spring-boot-maven-plugin
COPY --from=builder /build/target/*.jar /app/app.jar

# Basic JVM tuning for container (adjust Xmx to your environment)
ENV JAVA_OPTS="-Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]



