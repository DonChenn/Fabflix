# Use an official Tomcat runtime as a parent image
FROM tomcat:10.1-jdk11-openjdk

# Remove the default Tomcat webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy the WAR file as ROOT.war to deploy it at the root context
COPY login-service/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war

# Copy the MySQL connector
COPY login-service/target/mysql-connector-j-8.0.33.jar /usr/local/tomcat/lib/

# Expose port 8080
EXPOSE 8080

# Start Tomcat
CMD ["catalina.sh", "run"]