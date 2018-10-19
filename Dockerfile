FROM openjdk:8

COPY resources /usr/src/social-media-analysis-live/resources

COPY target/social-media-analysis-live-1.1-jar-with-dependencies.jar /usr/src/social-media-analysis-live

WORKDIR /usr/src/social-media-analysis-live

CMD ["java", "-jar", "social-media-analysis-live-1.1-jar-with-dependencies.jar"]
