# 云端部署说明（把链接发给别人用）

**使用阿里云**：可直接按 **[阿里云部署.md](./阿里云部署.md)**（安全组、域名、HTTPS、镜像加速等控制台步骤）。

目标：**一个 HTTPS（或至少 HTTP）链接**，打开即可注册、登录、聊天。推荐 **同一域名** 托管前端静态页 + 反向代理 API 与 WebSocket，这样无需跨域，验证码 Cookie 也最省事。

---

## 架构说明

| 路径 | 转到 |
|------|------|
| `/` | Vue 打包产物（`chat-web/dist`） |
| `/chat/` | Spring Boot（默认容器内 `5555`，context-path `/chat`） |
| `/socket.io/` | netty-socketio（默认容器内 `9999`） |

浏览器始终访问 **同一个域名**（例如 `https://chat.example.com`），由 Nginx 分流。

---

## 方案 A：一台云服务器 + Docker Compose（适合课程演示）

适用：阿里云 / 腾讯云 **轻量应用服务器**、华为云 ECS 等（有公网 IP）。

### 1. 服务器准备

- 系统建议：**Ubuntu 22.04 LTS**
- 安装 **Docker** 与 **Docker Compose V2**
- 安全组 / 防火墙放行：**80**（HTTP）、**443**（HTTPS，配置证书后）

### 2. 上传代码并构建前端

在服务器上克隆或上传本项目后：

```bash
cd chat-web
npm ci
npm run build
cd ..
```

（同域部署时不要设置 `VITE_API_ORIGIN`，保持 `chat-web/.env.production` 为空或不创建该文件。）

### 3. 启动整套栈

```bash
cd deploy
export JWT_SECRET="请换成至少32位的随机字符串"
docker compose -f docker-compose.stack.yml up -d --build
```

首次会自动：`docker build` 后端、`pull` Mongo/Redis/Nginx，并挂载 `../chat-web/dist`。

浏览器访问：**`http://服务器公网IP`** 即可让别人使用。

### 4. 绑定域名 + HTTPS（强烈推荐）

1. 在域名 DNS 增加 **A 记录** 指向服务器 IP。
2. 修改 `deploy/nginx.conf` 里 `server_name _;` 为你的域名，例如 `server_name chat.example.com;`。
3. 在服务器安装 [Certbot](https://certbot.eff.org/)，按指引为 Nginx 申请 Let’s Encrypt 证书，或手动增加 `listen 443 ssl` 与证书路径。

HTTPS 上线后，把 **`https://你的域名`** 发给他人即可。

### 5. 数据与安全提示

- Compose 内的 **MongoDB 未设密码**，仅适合演示；正式环境请改为 **独立云数据库**（如 MongoDB Atlas）并在 `docker-compose.stack.yml` 里把 `MONGODB_URI` 换成Atlas连接串，且不要把数据库端口暴露到公网。
- **JWT_SECRET**、Redis 密码等务必使用强随机值，且不要提交到 Git。
- 定期备份 Mongo 数据卷或云数据库。

---

## 方案 B：前后端分开放（静态托管 + 独立 API）

例如：前端托管在 **Cloudflare Pages / Vercel**，后端跑在 **Railway / Render / 自建 VPS**。

1. **后端** 暴露公网访问（HTTP + WebSocket），记下 API 根地址，例如 `https://api.example.com`（需同时能访问 `https://api.example.com/chat/...` 与 `wss://api.example.com/socket.io/`）。
2. **前端构建** 前新建 `chat-web/.env.production`：

```env
VITE_API_ORIGIN=https://api.example.com
VITE_SOCKET_ORIGIN=https://api.example.com
```

然后执行 `npm run build`，把 `dist` 上传到静态托管。

3. **后端环境变量** 设置：

```text
CORS_ALLOWED_ORIGIN_PATTERNS=https://你的前端站点域名
```

（逗号分隔多个来源；不要写 `*`，且使用 `allowCredentials` 时不能使用 `*`。）

4. 登录验证码依赖 Cookie：**跨站** 时需后端 Cookie `SameSite=None; Secure` 且全站 HTTPS，实现成本较高；因此 **课程作业优先推荐方案 A 同域部署**。

---

## 环境变量清单（生产）

| 变量 | 说明 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `MONGODB_URI` | MongoDB 连接串 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_SSL` | Redis |
| `JWT_SECRET` | JWT 签名密钥 |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | 仅前后端不同域时需要 |
| `SOCKETIO_ENABLED` | `true` |
| `FASTDFS_NGINX_HOST` | 若暂未接文件服务可保留默认 |

详见仓库根目录 `.env.example`。

---

## 常见问题

**打不开聊天 / 消息发不出去**  
检查云厂商安全组是否放行端口；若用 Nginx，确认 `/socket.io/` 已按示例做 **WebSocket 升级**（`Upgrade`、`Connection`）。

**502 Bad Gateway**  
后端未启动或容器名不是 `chatserver`；若改服务名，需同步修改 `nginx.conf` 里 `proxy_pass` 主机名。

**静态页空白**  
确认已执行 `npm run build`，且 `docker-compose.stack.yml` 把 `../chat-web/dist` 挂进 Nginx。
