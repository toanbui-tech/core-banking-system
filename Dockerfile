# Build stage - dùng image Maven chính thức (pin version khớp .mvn/wrapper/maven-wrapper.properties)
# thay vì ./mvnw, vì maven-wrapper.jar bị gitignore -> không đảm bảo có sẵn khi build từ git clone sạch.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src src
RUN mvn -q -B clean package -DskipTests

# Runtime stage - chỉ JRE, không cần JDK/Maven trong image chạy thật
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
