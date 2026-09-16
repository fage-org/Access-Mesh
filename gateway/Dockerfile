# AccessMesh gateway 镜像（T-ACCESS-047）
# 形态：宿主机构建 + 瘦镜像拷 jar——先执行 mvn package -DskipTests
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY target/*.jar app.jar

EXPOSE 8080
# jar 落在 WORKDIR(/app) 下的 app.jar——路径相对 WORKDIR，勿写 /app.jar 根路径（T-ACCESS-047 实证）
ENTRYPOINT ["java", "-jar", "app.jar"]
