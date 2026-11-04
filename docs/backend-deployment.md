# 后端容器部署

在仓库根目录构建，平台目标目录留空（如果平台要求路径则填 .），Dockerfile 选择“有”，文件名填写 Dockerfile，服务端口填写 8123。

Dockerfile 在镜像构建时执行 Maven 打包，运行环境包含 Java 21、Node.js 和高德 MCP 服务。默认激活 prod,mcp，生产配置关闭接口文档页面。镜像构建需要能够访问 Maven、npm 和容器镜像仓库。

## 平台环境变量

- AI_DASHSCOPE_API_KEY：通义千问 Key。
- AI_DASHSCOPE_BASE_URL：该 Key 对应的 OpenAI 兼容地址，沿用本地配置中的地址。业务空间 Key 应使用对应业务空间地址。
- DEEPSEEK_API_KEY：图片识别 Key。
- AMAP_MAPS_API_KEY：高德 Web 服务 Key，默认启用 MCP 时必须填写。
- SEARCH_API_KEY：联网搜索 Key，使用搜索工具时需要。

只将值填写到平台环境变量，不写入 Dockerfile 或公开配置。
如果暂时不用地图，将 SPRING_PROFILES_ACTIVE 设置为 prod，之后配置地图再改为 prod,mcp。

## 验证

部署后访问 /api/health 检查服务，聊天接口保留 /api 前缀。
前端需要另外构建部署，并将 /api 请求代理到这个后端。
本地 Vite 的代理目标 localhost:8123 仅用于本机开发。

## 数据保存

为 /app/data 配置持久化存储，保留会话数据库。
为 /app/tmp 配置持久化存储，保留生成的文件。
当前 HSQLDB 文件库应使用单实例部署，多副本需先迁移到支持共享的数据库。
没有挂载持久化存储时，替换容器可能丢失会话和生成的文件。

## 本机安装 Docker 后构建

    docker build -t saturday-pet-backend .

使用容器平台的日志检查启动结果。默认监听 8123，生产环境不开放 Knife4j 页面。
