# AccessMesh example-service 镜像（T-ACCESS-047）
# 形态：宿主机构建 + 瘦镜像拷 jar——先执行 mvn package -DskipTests
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY target/*.jar app.jar

EXPOSE 9300
ENTRYPOINT ["java", "-jar", "app.jar"]
