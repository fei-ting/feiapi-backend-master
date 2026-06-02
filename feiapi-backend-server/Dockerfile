FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build/feiapi-backend-master

COPY pom.xml ./pom.xml
COPY feiapi-common ./feiapi-common
COPY feiapi-client-sdk ./feiapi-client-sdk
COPY feiapi-backend-server ./feiapi-backend-server
COPY feiapi-gateway ./feiapi-gateway
COPY feiapi-interface ./feiapi-interface

RUN mvn -pl feiapi-backend-server -am clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

COPY --from=builder /build/feiapi-backend-master/feiapi-backend-server/target/feiapi-backend-server-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 9527

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
