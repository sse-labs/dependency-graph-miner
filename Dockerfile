FROM maven:3.6.0-jdk-8-alpine

RUN mkdir /usr/app
RUN mkdir /index
RUN mkdir /workdir

COPY ./miner/ /usr/app/miner/
RUN mvn clean compile assembly:single -DskipTests -f /usr/app/miner/pom.xml

WORKDIR /usr/app/miner

ENTRYPOINT [ "java", "-jar", "./target/maven-miner-1.0-SNAPSHOT-jar-with-dependencies.jar" ]