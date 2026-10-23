# syntax=docker/dockerfile:1
# ==============================================================================
# Dockerfile multi-stage do Treino de Concursos (RNF03, RNF09)
#   1. Node 22 -> build do Vite (ilhas publicas + SPA do painel + manifest.json)
#   2. Maven + JDK 21 -> empacota o .jar do Spring Boot com o frontend em static/
#   3. Runtime JRE 21 enxuto, usuario sem privilegios e JVM limitada a -Xmx512m
# ==============================================================================

# --- Etapa 1: Build do frontend (Vite) ---
FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# --- Etapa 2: Build do backend (Spring Boot + frontend copiado para static/) ---
FROM eclipse-temurin:21-jdk-alpine AS backend-build
WORKDIR /workspace/backend

COPY backend/.mvn ./.mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY backend/src ./src
# O maven-resources-plugin copia ../frontend/dist para target/classes/static
COPY --from=frontend-build /workspace/frontend/dist /workspace/frontend/dist
RUN ./mvnw -B package -DskipTests

# --- Etapa 3: Runtime de producao (JRE 21 com -Xmx512m) ---
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache tzdata \
    && addgroup -S treino \
    && adduser -S treino -G treino

ENV TZ=America/Sao_Paulo \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JAVA_OPTS="-XX:+UseContainerSupport"

WORKDIR /app
COPY --from=backend-build --chown=treino:treino /workspace/backend/target/*.jar /app/app.jar

USER treino:treino
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/sobre || exit 1

ENTRYPOINT ["sh", "-c", "exec java -Xmx512m ${JAVA_OPTS} -jar /app/app.jar"]
