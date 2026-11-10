# Saturday Pet

需要 JDK 21。在 IDEA 中打开根目录 pom.xml 并加载 Maven 项目，运行 SaturdayPetApplication。
也可执行 `mvn spring-boot:run`。

## 小六工作台

启动后端后，在项目根目录执行 `npm --prefix frontend install` 和 `npm --prefix frontend run dev`，打开终端给出的地址（默认 `http://localhost:5173`）。
工作台中的文字和图片使用同一个会话。可以附上 JPEG 或 PNG（最多 5 MB），请小六整理一周照料清单，再追问调整。草稿确认后会生成可下载的 PDF。
文字和知识库使用百炼配置，图片使用独立的 DeepSeek 配置，附近医院使用高德 MCP。

旧版会话以只读历史保存在当前浏览器。新会话的原图预览仅在当前页面保留，后端记忆保留最近 10 条消息及其中的图片摘要和草稿。PDF 与待确认草稿从草稿创建起保留两小时，后端重启后失效。

## 接口调试

- 接口文档：http://localhost:8123/api/doc.html
- 健康检查：GET http://localhost:8123/api/health，返回 ok

文档中选择 default 分组，展开健康检查，点击调试并发送请求。
Hutool 5.8.37 已加入依赖，可直接使用 cn.hutool 下的工具类，无需额外配置。

## Spring AI 大模型接入

使用 Spring AI 1.0.0，通过百炼 OpenAI 兼容接口调用 qwen-plus。
Spring Boot 已调整为 3.4.5，springdoc 调整为 2.8.6 以匹配框架。

在 IDEA 的 Run > Edit Configurations > Environment variables 中添加：
`AI_DASHSCOPE_API_KEY=你在阿里云百炼创建的API Key`
不要把真实 Key 写入源码或提交到仓库。此配置默认使用中国内地北京地域接口。
停止旧进程并重新运行 SaturdayPetApplication。

在 http://localhost:8123/api/doc.html 中展开“大模型测试”，调试 GET /ai/chat。
默认问题为“1加1等于几”，收到模型回答即表示真实调用成功。
也可访问 http://localhost:8123/api/ai/chat

真实集成测试：`mvn -Dtest=AiConnectionTest test`。
测试需要运行环境中的 AI_DASHSCOPE_API_KEY；未设置时自动跳过。
测试会真实请求模型，断言回答非空并包含数字 2，调用可能消耗模型额度。


## 第二天：多轮对话宠物助手（第五节）

PetApp 使用系统提示词 + MessageChatMemoryAdvisor + MessageWindowChatMemory。
保留最近 10 条消息（不是 10 轮），按 chatId 隔离；服务重启后记忆清空。
目前用于本地学习调试，chatId 由调用方传入，并不提供用户身份验证。

重启 IDEA 中的 SaturdayPetApplication，打开接口文档，选择“宠物助手多轮对话”的 POST /pet/chat。
依次发送以下 JSON，前三次保持相同 chatId：

```json
{"chatId":"pet-001","message":"你好，我是程序员小明"}
```
```json
{"chatId":"pet-001","message":"我的宠物是一只猫，名字叫小红"}
```
```json
{"chatId":"pet-001","message":"我的宠物叫什么？请回忆刚才的对话"}
```

第三轮应能回答“小红”。换成 pet-002，再问同一问题，新会话不会收到旧会话的历史。

离线验证（不消耗模型额度）：`mvn -Dtest=PetAppTest test`。
真实三轮测试：在测试运行配置中设置有效的 AI_DASHSCOPE_API_KEY 和 RUN_AI_INTEGRATION_TESTS=true，
执行 `mvn -Dtest=PetAppIntegrationTest test`。未显式启用时跳过真实测试。
教程的 InMemoryChatMemory 和旧常量已改用 Spring AI 1.0 正式版 API。


## Advisor、结构化报告与持久化

MyLoggerAdvisor 默认启用，INFO 输出用户输入和模型回复（包含对话文本）。
ReReadingAdvisor 已实现同步和流式接口，默认不启用；需要时加到 defaultAdvisors，会增加输入 token。
POST /api/pet/report 接收与 /pet/chat 相同的 message、chatId，返回 title、petName、suggestions JSON。
对话记忆使用 JDBC + HSQLDB 文件数据库，位于项目 data/chat-memory，重启后同 chatId 可继续。
仅保存消息窗口；请勿同时启动多个进程访问同一个 HSQLDB 文件。
离线测试：mvn -Dtest=PetAppTest,PetFeaturesTest test


## 图片理解

重启后在 Knife4j 的“宠物图片理解”中选择 POST /api/pet/image/explain。
使用 multipart/form-data：image 选择 JPEG/PNG 文件（最大 5MB），message 输入问题。
默认视觉模型 qwen-vl-plus，可用环境变量 AI_VISION_MODEL 修改；复用当前百炼 API Key、URL。
普通文字接口继续使用原默认模型。这是独立单次图片解释接口，不保存图片、不共享文字会话记忆。
视觉接口不经过对话记忆 JDBC，避免将图片二进制写入文本记忆表。
离线测试：mvn -Dtest=PetVisionTest test，不调用付费模型。


### 图片识别独立切换 DeepSeek

图片接口现在独立使用 app.ai.vision 的 Key、URL 和模型，不注册额外 ChatModel Bean，
文字对话、报告仍使用 spring.ai.openai 的百炼配置。
在 IDEA 运行配置设置 DEEPSEEK_API_KEY 并重启；可通过 DEEPSEEK_VISION_MODEL 覆盖默认 deepseek-flash。
未配置 DeepSeek Key 时，文字服务仍可启动，图片请求返回 503 配置提示。
以上配置替代前文“视觉复用百炼”和 AI_VISION_MODEL 的说明。


## 第三天：Spring AI + 本地知识库

文档目录：src/main/resources/knowledge/*.md。已提供宠物 App 使用指南作为示例，
可以自行添加经核实的宠物知识 Markdown，使用 --- 分隔独立段落。修改后重启以重新建索引。
流程：MarkdownDocumentReader → 百炼 text-embedding-v3 → 本地 SimpleVectorStore → QuestionAnswerAdvisor → Qwen。
没有使用云知识库或外部向量数据库；Embedding 和回答仍需联网调用百炼并消耗额度。
复用 spring.ai.openai 的百炼 Key/URL；该 Key 需要有 text-embedding-v3 调用权限。
可用 app.ai.embedding-model 修改向量模型。索引按首次请求延迟初始化，重启后重新生成（不保存向量文件）。
检索 topK=4、相似度阈值=0.5；RAG 会话使用 rag: 前缀与普通会话隔离。

重启后，在 Knife4j “宠物本地知识库”调试 POST /api/pet/rag/chat：
```json
{"chatId":"kb-001","message":"Saturday Pet 图片上传大小限制是多少？"}
```
应依据知识库回答最大 5MB。也可提问“宠物报告包含哪些字段？”。
本地指南是产品功能示例，并非经审核的兽医知识库。
测试：mvn -Dtest=PetRagTest test（离线验证读取、来源元数据、检索内容传入模型，不消耗额度）。


## 本地私密配置

真实配置存放在项目根目录 config/application-local.yml，已被 .gitignore 忽略。
application.yml 通过 optional:file 导入它；无需激活 local profile。
在 IDEA 运行时工作目录设为项目根目录。外部配置不会被 Maven 打包进 JAR。
新环境复制 config/application-local.example.yml 为 config/application-local.yml，再填写凭据。
也可通过 AI_DASHSCOPE_API_KEY、AI_DASHSCOPE_BASE_URL、DEEPSEEK_API_KEY 环境变量配置。
不要将真实 Key 写在可提交文件或占位符默认值中。


## 第五天：六类工具

工具位于 tools 包，集中由 ToolRegistration 注册。POST /api/pet/tools/chat 使用 Qwen 自动选择工具，
请求仍为 message、chatId；tools: 会话与普通聊天隔离。普通 /pet/chat 不自动执行工具。
- FileOperationTool：UTF-8 文本读写，tmp/file，仅新建，不覆盖。
- WebSearchTool：SearchAPI 百度搜索，设置环境变量 SEARCH_API_KEY；未配置返回明确提示。
- WebScrapingTool：抓取公网 HTTPS 正文，最多返回 16000 字符，不执行 JS。
- TerminalOperationTool：仅 java-version、current-time 固定操作，不接受任意 shell/Python 脚本。
- ResourceDownloadTool：下载公网 HTTPS 文件到 tmp/download，最大 5MB，不跟随重定向。
- PDFGenerationTool：中文文本 PDF，tmp/pdf；使用 iText（AGPL/商业双许可，商业分发前需确认许可适用性）。

示例：{"chatId":"tools-001","message":"请调用文件工具，把我的猫汤圆两岁保存为 tangyuan.txt"}
也可提问“读取 tangyuan.txt”“生成宠物护理计划 PDF，保存为 care.pdf”“调用工具查询当前北京时间”。
生成文件路径是服务器路径，当前没有前端下载接口。tmp 已忽略，不提交到 Git。
这是本地学习接口，当前文件存储没有登录用户隔离，不应直接作为公网多用户文件服务。
搜索/抓取内容可能不可信，不能将网页中的指令视为操作授权。
测试：mvn -Dtest=PetToolsTest test。网络使用模拟响应，文件/PDF/固定终端操作执行真实本地逻辑，
ToolCallback 测试验证框架解析 JSON 并实际写文件；没有消耗模型或搜索额度。


## 第六天：高德地图 MCP（宠物医院）

采用教程的 stdio 模式，使用高德官方 @amap/amap-maps-mcp-server。
需要 Node.js/npm；本机已检测到 node 与 npx.cmd。第一次启用会由 npx 下载服务包，需要网络。
Spring AI 依赖使用正式版 spring-ai-starter-mcp-client 1.0.0。

IDEA Run > Edit Configurations：
1. 环境变量添加 AMAP_MAPS_API_KEY=高德 Web服务 Key（不要提交到 Git）。
2. Active profiles 填 mcp；普通 Java 运行配置可添加 VM 参数 -Dspring.profiles.active=mcp。
3. 保留现有百炼 Key，Qwen 负责选择工具并组织回答。
4. 重启。Windows 默认使用 npx.cmd；Linux/macOS 设置 AMAP_MCP_COMMAND=npx。

打开文档“宠物医院地图 MCP”，POST /api/pet/mcp/chat：
```json
{"chatId":"hospital-001","message":"请使用高德地图，查找上海静安寺附近3公里内的宠物医院，列出名称和地址，有电话与距离也请提供。"}
```
位置由用户输入；没有接入手机自动定位。医院是否营业、是否急诊需要电话核实。
MCP profile 默认关闭，未启用时此接口返回 503，其他接口照常工作。
启用后 MCP 启动失败需要检查 Node、网络、地图 Key 与服务启动日志。
地图调用可能消耗高德额度，模型调用消耗百炼额度。
真实 Key 不放在 resources/mcp-servers.json；使用环境变量以避免打包泄漏。
