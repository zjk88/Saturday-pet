# 腾讯云静态前端连接后端

目标目录 frontend，Node.js 24，安装命令 npm ci --include=dev，构建命令 npm run build，构建产物 ./dist。

前端平台构建环境变量填写：

    VITE_API_BASE_URL=https://api.example.com

不要添加 /api，不填写任何模型 Key。修改这个构建变量后需要重新构建发布前端。

后端云托管环境变量 APP_WEB_ALLOWED_ORIGINS 填写实际前端的 HTTPS 域名，不带末尾斜线或路径。多个允许的来源用英文逗号分隔。修改后发布后端使配置生效。

在腾讯云和 Vercel 的前端构建环境变量中设置实际的后端 HTTPS 地址，并在后端的 APP_WEB_ALLOWED_ORIGINS 中加入对应的前端域名。上面的地址是文档占位值。本地开发可将 VITE_API_BASE_URL 留空，由 Vite 代理 /api。
