# 构建：docker build -t chatserver:latest .
# 运行需自行注入 MONGODB_URI、REDIS_*、JWT_SECRET 等环境变量（见 deploy/README.md）
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY --from=build /app/target/chatserver-*.jar /app/app.jar
EXPOSE 5555 9999
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
