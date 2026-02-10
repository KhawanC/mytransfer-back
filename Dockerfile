# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Fix line endings and make mvnw executable
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw package -DskipTests -B

# Production stage
FROM eclipse-temurin:21-jre-alpine AS runner

# Install ImageMagick for image conversion
RUN apk add --no-cache \
	imagemagick \
	libjpeg-turbo \
	libpng \
	libwebp \
	tiff \
	librsvg

WORKDIR /app

# Create non-root user
RUN addgroup --system --gid 1001 spring
RUN adduser --system --uid 1001 spring

# Copy the built jar
COPY --from=builder /app/target/*.jar app.jar

# Change ownership
RUN chown spring:spring app.jar

USER spring

EXPOSE 8080

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
