FROM --platform=linux/arm64 maven:3.9.6-eclipse-temurin-22 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

#---------RUNTIME STAGE---------------
FROM --platform=linux/arm64 eclipse-temurin:21-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java","-Xmx256m", "-Xms128m", "-jar", "app.jar"]