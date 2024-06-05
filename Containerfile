## ## ## ## ##
## BUILDER IMAGE
## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ##
FROM eclipse-temurin:21 as builder

# Build application
COPY . /app
WORKDIR /app
RUN ./mvnw clean package -Dprofile=prod -DskipTests -q

## ## ## ## ##
## RUN IMAGE
## ## ## ## ## ## ## ## ## ## ## ## ## ## ## ##
FROM eclipse-temurin:21-jre-alpine

## System parameter
ENV TZ "Europe/Berlin"

# Disable package manager
RUN rm -f /sbin/apk && \
    rm -rf /etc/apk && \
    rm -rf /lib/apk && \
    rm -rf /usr/share/apk && \
    rm -rf /var/lib/apk

# Prepare data folder
RUN mkdir -p /app/data
WORKDIR /app
COPY --from=builder /app/target/beatport-to-spotify-*.jar /app/beatport-to-spotify.jar

# Prepare user
RUN chown -R nobody:nobody . && chmod -R 777 /app
USER nobody

# Expose spring boots default port
EXPOSE 8080

# Expose the data directory
VOLUME ["/app/data"]

# Health check
HEALTHCHECK --interval=5s --timeout=5s --retries=3 \
    CMD ["wget", "--spider", "http://127.0.0.1:8080"]

# Specify entrypoint
ENTRYPOINT ["java", "-jar", "/app/beatport-to-spotify.jar"]