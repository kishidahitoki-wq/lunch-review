# ステージ1: ビルド
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
# mvnwに実行権限を与えてビルド
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

# ステージ2: 実行
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# ビルドステージからjarファイルをコピー
COPY --from=build /app/target/*.jar app.jar
# ポート番号はRenderの環境変数に合わせる
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]