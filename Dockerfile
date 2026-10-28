# Builds and runs the website version. Works on Render, Railway, Fly.io, Koyeb or any Docker host.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY src ./src
RUN javac --release 8 -nowarn -d bin $(find src -name '*.java')

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/bin ./bin
# PostgreSQL JDBC driver, used when DATABASE_URL is set (checksum from Maven Central)
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar lib/postgresql.jar
RUN echo "188976721ead8e8627eb6d8389d500dccc0c9bebd885268a3047180274a6031e  lib/postgresql.jar" | sha256sum -c -
COPY web ./web
# DEMO_DATA: an empty bank starts with demo accounts (the free host's disk is wiped on restart)
ENV HOST=0.0.0.0 PORT=8080 DEMO_DATA=true
EXPOSE 8080
CMD ["java", "-Djava.awt.headless=true", "-cp", "bin:lib/postgresql.jar", "WebApplication"]
