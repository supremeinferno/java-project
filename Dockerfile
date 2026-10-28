# Builds and runs the website version. Works on Render, Railway, Fly.io, Koyeb or any Docker host.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY src ./src
RUN javac --release 8 -nowarn -d bin $(find src -name '*.java') && cp -R src/img bin/

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/bin ./bin
COPY web ./web
ENV HOST=0.0.0.0 PORT=8080
EXPOSE 8080
CMD ["java", "-Djava.awt.headless=true", "-cp", "bin", "WebApplication"]
