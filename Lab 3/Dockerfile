FROM eclipse-temurin:23-jdk-alpine

WORKDIR /app

COPY out/production/ /app/build/
RUN cp -r /app/build/*/* /app/classes/ 2>/dev/null || cp -r /app/build/* /app/classes/

COPY src/main/resources/ /app/resources/

EXPOSE 8000

ENTRYPOINT ["java", "-cp", "/app/classes"]
CMD ["com.memorygame.server.GameServer", "/app/resources/boards/perfect.txt"]
