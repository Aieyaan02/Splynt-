# ------------------------------------------------------------
# Stage 1: Build the React and TypeScript frontend
# ------------------------------------------------------------
    FROM node:22-alpine AS frontend-build

    WORKDIR /workspace/frontend

    # Copy dependency files separately so Docker can cache npm install.
    COPY frontend/package.json frontend/package-lock.json ./

    RUN npm ci

    COPY frontend/ ./

    RUN npm run build


    # ------------------------------------------------------------
    # Stage 2: Build the Spring Boot application with Java 26
    # ------------------------------------------------------------
    FROM eclipse-temurin:26-jdk AS backend-build

    WORKDIR /workspace

    COPY .mvn .mvn
    COPY mvnw pom.xml ./

    RUN chmod +x mvnw

    COPY src src

    # Include the compiled React application in Spring Boot's
    # static web-resource directory.
    COPY --from=frontend-build \
        /workspace/frontend/dist \
        /workspace/src/main/resources/static

    # Tests already run before the image is built.
    # Retry downloads if Maven Central temporarily interrupts a connection.
    RUN ./mvnw \
        -B \
        -DskipTests \
        -Dmaven.wagon.http.retryHandler.count=5 \
        clean package


    # ------------------------------------------------------------
    # Stage 3: Create the production runtime image
    # ------------------------------------------------------------
    FROM eclipse-temurin:26-jre AS runtime

    WORKDIR /app

    COPY --from=backend-build \
        --chown=10001:10001 \
        /workspace/target/*.jar \
        /app/splynt.jar

    # Run Splynt as an unprivileged operating-system user.
    USER 10001:10001

    EXPOSE 8080

    ENV SPRING_PROFILES_ACTIVE=prod

    ENTRYPOINT ["java", "-jar", "/app/splynt.jar"]