# 1. 创建一个测试文件
echo "Spring Boot 是一个基于 Java 的开源框架，用于快速构建生产级应用。
它提供了自动配置、起步依赖和 Actuator 监控端点。
数据库连接池默认使用 HikariCP，支持 PostgreSQL、MySQL 等主流数据库。
配置文件支持 YAML 和 Properties 两种格式。" > /tmp/test-spring.md
# 2. 上传文档到 RAG 知识库
curl -X POST http://localhost:8080/api/v1/rag/documents \
  -F "file=@/tmp/test-spring.md"
# 3. RAG 问答
curl -X POST http://localhost:8080/api/v1/rag/query \
  -H 'Content-Type: application/json' \
  -d '{"question": "Spring Boot 用什么数据库连接池？"}'
# 如果已有 PDF/DOCX 文件，直接替换路径即可：
curl -X POST http://localhost:8080/api/v1/rag/documents \
  -F "file=@/path/to/your-doc.pdf"