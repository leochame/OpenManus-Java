# OpenManus Java Deployment Guide

## ? Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker
- 4GB+ RAM
- 2+ CPU cores

### Basic Deployment
```bash
# Clone repository
git clone https://github.com/OpenManus/OpenManus-Java.git
cd OpenManus-Java

# Build project
mvn clean package

# Start application
java -jar target/openmanus-java.jar
```

## ? Configuration

### Environment Variables
```bash
# Required
export OPENMANUS_LLM_API_KEY=your-api-key

# Optional
export SPRING_PROFILES_ACTIVE=prod
export SERVER_PORT=8080
export LOGGING_LEVEL_ROOT=INFO
```

### Configuration Files
- `application.yml` - Main configuration
- `application-dev.yml` - Development profile
- `application-prod.yml` - Production profile

### LLM Configuration
```yaml
openmanus:
  llm:
    default-llm:
      model: qwen-max
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1/
      api-type: openai
      temperature: 0.7
      max-tokens: 8192
      timeout: 120
```

## ? Docker Deployment

### Using Docker Compose
```yaml
# docker-compose.yml
version: '3.8'
services:
  openmanus:
    image: openmanus/openmanus-java:latest
    ports:
      - "8080:8080"
    environment:
      - OPENMANUS_LLM_API_KEY=${OPENMANUS_LLM_API_KEY}
      - SPRING_PROFILES_ACTIVE=prod
    volumes:
      - ./workspace:/app/workspace
```

### Manual Docker Build
```bash
# Build image
docker build -t openmanus/openmanus-java .

# Run container
docker run -p 8080:8080 \
  -e OPENMANUS_LLM_API_KEY=your-api-key \
  -v $(pwd)/workspace:/app/workspace \
  openmanus/openmanus-java
```

## ? Production Deployment

### System Requirements
- 8GB+ RAM
- 4+ CPU cores
- 20GB+ disk space
- Stable network connection

### Performance Tuning
```bash
# JVM options
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC"

# Application properties
spring.task.execution.pool.core-size=4
spring.task.execution.pool.max-size=8
spring.task.execution.pool.queue-capacity=100
```

### Security Settings
```yaml
# application-prod.yml
server:
  ssl:
    enabled: true
    key-store: keystore.p12
    key-store-password: ${SSL_KEY_STORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: openmanus

security:
  require-ssl: true
  basic:
    enabled: true
  user:
    name: ${ADMIN_USERNAME}
    password: ${ADMIN_PASSWORD}
```

## ? Monitoring

### Health Check
```bash
# Check application status
curl http://localhost:8080/actuator/health

# View metrics
curl http://localhost:8080/actuator/metrics
```

### Log Configuration
```yaml
# logback-spring.xml
logging:
  file:
    name: logs/openmanus.log
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  level:
    root: INFO
    com.openmanus: DEBUG
```

### Metrics Collection
- JVM metrics
- System metrics
- Business metrics
- API metrics

## ?? Security

### Network Security
- Enable HTTPS
- Configure firewall
- Set up reverse proxy
- Rate limiting

### Access Control
- API authentication
- Role-based access
- IP whitelisting
- Session management

### Data Security
- Encrypt sensitive data
- Regular backups
- Audit logging
- Data retention policy

## ? Maintenance

### Backup Strategy
```bash
# Backup workspace
tar -czf backup.tar.gz workspace/

# Backup configuration
cp application*.yml backups/
```

### Update Process
1. Backup current version
2. Download new version
3. Update configuration
4. Restart service

### Troubleshooting
- Check logs
- Monitor resources
- Review metrics
- Test endpoints

## ? Checklist

### Pre-deployment
- [ ] Set environment variables
- [ ] Configure SSL
- [ ] Set up monitoring
- [ ] Test security

### Post-deployment
- [ ] Verify endpoints
- [ ] Check logs
- [ ] Monitor performance
- [ ] Test features

## ? Support

### Common Issues
1. Memory issues
   - Increase JVM heap size
   - Check memory leaks
   - Monitor GC

2. Connection issues
   - Check network
   - Verify API keys
   - Test endpoints

3. Performance issues
   - Monitor resources
   - Optimize configuration
   - Scale resources

### Getting Help
- GitHub Issues
- Documentation
- Community forum
- Support email

## ? References

### Documentation
- [Spring Boot](https://spring.io/projects/spring-boot)
- [LangChain4j](https://github.com/langchain4j/langchain4j)
- [Docker](https://docs.docker.com)

### Tools
- [Spring Boot Admin](https://codecentric.github.io/spring-boot-admin)
- [Prometheus](https://prometheus.io)
- [Grafana](https://grafana.com)

### Resources
- Project Wiki
- API Documentation
- Architecture Guide
- User Guide 