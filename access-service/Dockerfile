# AccessMesh access-service 镜像（T-ACCESS-047）
# 形态：宿主机构建 + 瘦镜像拷 jar（不在 Docker 内跑 Maven）——先执行：
#   mvn package -DskipTests
# 再 docker compose --profile app up -d --build
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
# repackaged fat jar 为 target/ 下唯一 *.jar（.jar.original 不匹配通配）；落位 /app/app.jar
COPY target/*.jar app.jar

EXPOSE 9100
ENTRYPOINT ["java", "-jar", "app.jar"]
