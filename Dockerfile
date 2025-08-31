FROM almalinux:8
RUN dnf -y install java-21-openjdk-headless curl && dnf clean all
ENV SERVER_PORT=8081 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
WORKDIR /opt/app
COPY target/audit-sink-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
