FROM eclipse-temurin:19-jdk-jammy AS build

WORKDIR /app

# Copy Gradle files first for better layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (cached layer if build.gradle doesn't change)
RUN ./gradlew dependencies --no-daemon || true

# Copy source files (separate layer)
COPY src src

# Build with parallel execution and build cache
RUN ./gradlew bootJar --no-daemon --parallel --build-cache

# Runtime stage
FROM eclipse-temurin:19-jre-jammy

WORKDIR /app

# Create directory for index files
RUN mkdir -p /app/index

# Copy the built JAR
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port (Spring Boot default is 8080)
EXPOSE 8080

# Set default JVM options for container environments with performance optimizations
ENV JAVA_OPTS="-Xmx4g -Xms4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:+OptimizeStringConcat -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -Djava.security.egd=file:/dev/./urandom"
ENV INDEX_DIR="/app/index"

# Run the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=web --indexdir=${INDEX_DIR:-/app/index}"]

