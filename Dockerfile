FROM openjdk:17-jdk-slim-buster
WORKDIR /app
ENV PORT 8080
EXPOSE 8080
COPY target/*.jar /app/app.jar
ENV WORKER_NAME="salem"
ENTRYPOINT ["sh", "/app/startup.sh"]