#!/bin/bash
set -e

echo "========================================"
echo "  Agent Flow 一键部署脚本"
echo "========================================"

# 1. 检查 .env 文件
if [ ! -f .env ]; then
    echo "[1/4] .env 文件不存在，从 .env.example 创建..."
    cp .env.example .env
    echo "  -> 请编辑 .env 填入真实 API Keys，然后重新运行此脚本"
    exit 1
fi
source .env

# 2. 构建 JAR
echo "[2/4] 构建项目..."
./mvnw clean package -DskipTests -q

# 3. 启动 Docker 中间件
echo "[3/4] 启动 PostgreSQL + Redis + Neo4j..."
docker compose up -d postgres redis neo4j

# 4. 启动应用
echo "[4/4] 启动 Agent Flow 应用..."
./mvnw spring-boot:run &

echo "========================================"
echo "  部署完成！"
echo "  API:      http://localhost:8080"
echo "  Admin UI: http://localhost:3000 (cd frontend && npm run dev)"
echo "  Neo4j:    http://localhost:7474"
echo "========================================"
