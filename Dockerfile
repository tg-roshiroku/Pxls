# Use Eclipse Temurin 21 as base image (more secure)
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install necessary packages including Maven
RUN apk add --no-cache \
    curl \
    wget \
    maven

# Set working directory
WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml ./

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src
COPY resources ./resources

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built jar from builder stage
COPY --from=builder /app/target/pxls-1.0-SNAPSHOT.jar ./pxls.jar

# Copy configuration files
COPY docker-pxls.conf ./pxls.conf
COPY resources/palette-reference.conf ./palette.conf
COPY resources/roles-reference.conf ./roles.conf
COPY extras ./extras

# Create board data directory
RUN mkdir -p /app/board
RUN apk add --no-cache python3 py3-pip
RUN pip3 install --break-system-packages pyhocon psycopg2-binary

# Expose the port (default is 4567 for Spark Java)
EXPOSE 4567

# Run the application
CMD ["java", "-jar", "pxls.jar"]
