# 即时通讯系统（后端 chatserver + 前端 chat-web）

全栈网页聊天：**后端** REST API + JWT + Socket.IO（netty-socketio），MongoDB / Redis；**前端** Vue 3 + Vite + Element Plus（见目录 `chat-web`）。

## 技术栈

| 组件 | 说明 |
|------|------|
| Spring Boot 2.4 | Web、Security、AOP |
| Spring Security + JWT | 无状态登录与鉴权 |
| MongoDB | 业务数据 |
| Redis | 验证码、在线用户等 |
| netty-socketio | 与前端 Socket.IO 协议交互 |
| FastDFS（可选） | 图片/文件存储 |
| Swagger 2 | API 文档：`http://主机:端口/chat/swagger-ui.html` |
| Vue 3 + Vite | 前端 `chat-web`，默认开发端口 `5173` |

## 快速开始（本地）

### 1. 启动依赖服务

在项目根目录执行：

```bash
docker compose up -d
```

将启动 MongoDB（`27017`）与 Redis（`6379`）。若使用云数据库，可跳过此步，并通过环境变量指向远端。

### 2. 配置

- 默认激活 **`dev`** 配置（见 `src/main/resources/application.yml`）。
- `application-dev.yml` 已改为通过**环境变量**覆盖连接信息，默认指向本机 `docker compose`。
- 如需本地私有覆盖且不提交仓库，可在 `src/main/resources/` 下新增 **`application-local.yml`**（已在 `.gitignore` 中忽略），并在其中编写个人配置。
- 可参考仓库根目录 **`.env.example`**，将变量导入系统环境或使用你自己的配置方式。

**重要环境变量示例：**

| 变量 | 说明 |
|------|------|
| `MONGODB_URI` | MongoDB 连接串，默认 `mongodb://localhost:27017/chatdb` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_SSL` | Redis 连接 |
| `JWT_SECRET` | JWT 签名密钥，**生产环境必须改为长随机串** |
| `SOCKETIO_ENABLED` | 是否启动 Socket.IO（测试可设为 `false`） |
| `SOCKETIO_PORT` | Socket.IO 端口，默认 `9999` |
| `FASTDFS_NGINX_HOST` | FastDFS 对外 Nginx 访问地址 |

### 3. 运行

```bash
mvn spring-boot:run
```

HTTP 服务默认端口 **`5555`**，上下文路径 **`/chat`**。  
健康检查（Spring Boot Actuator）默认在 **`9555`** 端口（可通过 `MANAGEMENT_PORT` 修改）。

### 4. 生产 Profile

```bash
export SPRING_PROFILES_ACTIVE=prod
export MONGODB_URI="mongodb://用户:密码@主机:27017/chatdb"
export JWT_SECRET="至少32字符的随机密钥"
# … 按需设置 REDIS_*、FASTDFS_NGINX_HOST 等
java -jar target/chatserver-0.0.1-SNAPSHOT.jar
```

打包：

```bash
mvn -DskipTests package
```

（默认跳过测试时镜像构建更快；提交前建议在装有 Docker 的环境执行 `mvn test` 做一次完整校验。）

### 5. 前端（chat-web）

1. 确保后端已启动：`5555`（HTTP）、`9999`（Socket.IO，`socketio.enabled=true`）。
2. 安装并启动：

```bash
cd chat-web
npm install
npm run dev
```

3. 浏览器打开 **http://localhost:5173**。开发环境下 Vite 会将 **`/chat`** 代理到 **`http://localhost:5555`**，将 **`/socket.io`** 代理到 **`http://localhost:9999`**（与后端默认端口一致）。

当前前端已实现：**注册 / 登录（含验证码 Cookie）**、**好友列表**、**单聊（HTTP 拉历史 + Socket.IO 实时收发）**、在线状态绿点。加好友验证、群聊、文件图床等可在现有接口与 `SocketIoListener` 事件上继续扩展。

生产构建：`cd chat-web && npm run build`，将 `dist` 静态资源交给 Nginx 等服务端，并配置反代到同一域下的 `/chat` 与 WebSocket/Socket.IO 路径。

**把网站部署到云端、通过链接分享给他人**：零基础请直接打开 **`deploy/小白-阿里云一步步.md`** 按步骤操作；通用说明见 **`deploy/README.md`**；阿里云控制台要点见 **`deploy/阿里云部署.md`**。仓库已提供 **`Dockerfile`**、`deploy/docker-compose.stack.yml`、`deploy/nginx.conf`。

### 6. 测试说明

- **`ChatServerApplicationTests`**：完整 Spring 上下文测试，使用 **Testcontainers** 自动拉起 MongoDB / Redis。**需要本机安装并运行 Docker**；若未安装 Docker，该测试会被跳过（不计失败）。
- **`ManualIntegrationTests`**：历史调试/演示用例，依赖固定业务数据与本机路径，类上带有 **`@Disabled`**，仅在本地按需去掉禁用后运行。

## API 文档与演示

- Swagger UI：`/swagger-ui.html`（注意统一加上 context-path `/chat`）。
- 功能概览（摘自原设计）：私聊、群聊、图片/文件上传、敏感词过滤、历史消息、已读、好友分组与备注、在线状态、好友/群组管理等；音视频与白板等多依赖前端与额外中继服务（如 coturn），需单独部署。
- 演示视频：[Bilibili](https://www.bilibili.com/video/bv1Xo4y1C7Bv)

## 安全与运维建议

1. **切勿将数据库密码、JWT 密钥、Redis 密码提交到 Git**。若曾提交过明文凭证，请在服务商控制台**轮换凭证**，并清理历史提交（如 `git filter-repo`）。
2. 生产环境务必设置强随机 **`JWT_SECRET`**（已通过配置外部化，不再写死在代码中）。
3. FastDFS、MongoDB、Redis、管理端口等请勿在未加固情况下暴露公网。
4. 依赖 **FastJSON** 已升级到 **1.2.83**；后续如有精力建议逐步迁移至 Jackson，降低维护风险。

## 相关文档（外部）

- MongoDB 安装：[Windows](https://www.jianshu.com/p/2ab39e37d0fb) · [CentOS](https://www.jianshu.com/p/681d584d9281)
- FastDFS + Nginx：[CentOS 搭建示例](https://www.jianshu.com/p/e60797e328d3)

## 许可证

课程/作业项目，使用权与许可证以课程要求为准。
