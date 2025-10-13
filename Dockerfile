# Multi-stage build for YouTube Transcode Service
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy the POM file
COPY pom.xml .

# Download all dependencies
# This step is separated to leverage Docker layer caching
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre

# Install FFmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Create directories for video storage
RUN mkdir -p /app/temp-uploads /app/transcoded-videos

# Copy the built artifact from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Make storage directories accessible
VOLUME ["/app/temp-uploads", "/app/transcoded-videos"]

# Expose gRPC port
EXPOSE 9090

# Set environment variables
ENV STORAGE_TEMP_DIRECTORY=/app/temp-uploads
ENV STORAGE_OUTPUT_DIRECTORY=/app/transcoded-videos

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

