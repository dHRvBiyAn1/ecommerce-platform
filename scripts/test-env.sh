#!/bin/bash
echo "Testing services..."
curl -f http://localhost:8081/hello && echo " Hello-world OK"
curl -f http://localhost:8761 && echo " Eureka OK"
curl -f http://localhost:8888/actuator/health && echo " Config Server OK"
echo "All good!"