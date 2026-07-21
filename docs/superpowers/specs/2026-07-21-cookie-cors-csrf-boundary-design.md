# Cookie、跨域与 CSRF 安全边界整改设计

## 1. 文档信息

- 设计日期：2026-07-21
- 适用范围：FeiAPI 管理前端、管理后端、前端 Nginx 与 Docker Compose 部署配置
- 对应审查项：`前端代码架构审查报告.md` 7.5 Cookie、跨域与 CSRF 边界
- 设计状态：方案已确认，等待书面设计审阅

## 2. 背景与问题

当前生产构建已经默认使用同源 `/api`，前端 Nginx 会将管理请求转发至后端。后端生产配置也已设置 Session Cookie 的 `SameSite=Lax` 与 `Secure=true`，因此 `withCredentials: true` 和现有 Cookie 配置本身不能直接判定为漏洞。

复核仍发现以下安全边界没有闭环：

1. 管理后端没有 CSRF 令牌校验，状态修改接口主要依赖 Cookie、CORS 和 `SameSite=Lax`。
2. 管理后端 CORS 硬编码允许本地开发 Origin，生产同源部署仍保留不必要的跨域面。
3. Session Cookie 没有显式声明 `HttpOnly` 和 `Path`，并通过 `Domain` 配置扩大了 Cookie 作用域。
4. 登录成功后没有主动轮换 Session ID，退出时仅删除用户属性，没有销毁整个 Session。
5. Docker Compose 直接发布后端和网关原始端口，调用方可能绕过边缘代理。
6. 前端 Nginx 缺少 CSP、点击劫持防护、MIME 嗅探防护和引用来源策略等响应头。

## 3. 目标与非目标

### 3.1 目标

1. 以成熟框架为所有管理后端写请求提供统一 CSRF 校验。
2. 将本地开发和生产浏览器请求统一为同源 `/api` 数据流。
3. 明确 Session Cookie 和 CSRF Cookie 的不同安全属性与生命周期。
4. 防止登录会话固定，并确保退出后整个 Session 失效。
5. 让管理后端只在 Docker 内部网络可达，让 SDK 网关通过独立受控入口开放。
6. 为前端页面和静态资源补齐可验证的浏览器安全响应头。
7. 通过真实启用 CSRF 的自动化测试验证安全边界，不在测试配置中关闭防护。

### 3.2 非目标

1. 本次不重写现有 Session 登录、AOP 权限校验或角色模型。
2. 本次不修改网关 AK/SK 签名、时间戳、Nonce、限流和计费业务逻辑。
3. 本次不允许第三方浏览器应用跨域访问管理后端；未来出现明确需求时再单独设计白名单。
4. 本次不清理全部内联样式，CSP 暂时允许内联样式但禁止内联脚本。
5. 本次不使用 `Origin`、`Referer` 或 Fetch Metadata 校验替代 CSRF 令牌；这些能力仅作为后续纵深防御候选项。

## 4. 部署架构

管理端与 SDK 调用端采用双入口，所有原始服务保持私有：

```text
浏览器
  -> https://console.example.com/
  -> 边缘代理
       -> 前端静态资源
       -> /api/** -> feiapi-backend:9527

外部 SDK
  -> https://gateway.example.com/**
  -> WAF / 边缘代理 / 负载均衡
       -> feiapi-gateway:8090
       -> 内部接口服务
```

管理入口使用 Session Cookie 和 CSRF；SDK 网关使用 AK/SK 请求签名，不使用浏览器 Session Cookie，因此不属于本次 CSRF 数据流。

单机 Docker Compose 部署时，管理后端不再映射宿主机端口。前端和网关仅绑定宿主机回环地址，供最外层代理访问：

```text
feiapi-backend: 不配置宿主机 ports
feiapi-frontend: 127.0.0.1:8000:80
feiapi-gateway: 127.0.0.1:8090:8090
```

集群部署时应使用私有 Service 或内部负载均衡替代回环绑定，原则仍是原始服务端口不直接对公网开放。

## 5. 组件职责

### 5.1 前端 Nginx

1. 提供前端静态资源。
2. 将 `/api/**` 转发至管理后端。
3. 通过公共配置片段向页面、静态资源和 API 代理等所有浏览器可见响应添加安全响应头。
4. 不承担 HSTS 策略；HSTS 由实际终止 HTTPS 的最外层代理设置。

### 5.2 Vite 开发服务器

1. 将 `/api/**` 代理至 `http://localhost:9527`。
2. 保证浏览器始终请求当前 Origin，不再由前端直接跨域访问后端端口。

### 5.3 前端 HTTP 层

1. API 基址固定为 `/api`，移除 `VITE_API_BASE` 分支。
2. 保留 `withCredentials: true`，并显式配置 Axios 的 XSRF Cookie 与 Header 名称。
3. 写请求发送前确保浏览器已取得 CSRF Cookie。
4. 并发写请求复用同一个令牌初始化请求。
5. CSRF 失败后不自动重放写请求。

### 5.4 Spring Security 层

1. 使用 `CookieCsrfTokenRepository` 校验双提交 Cookie。
2. 使用 `CsrfTokenRequestAttributeHandler` 接收 Axios 发送的原始 Header 令牌。
3. 所有 URL 授权规则保持 `permitAll`，继续由现有 AOP 和 Session 逻辑完成身份与角色鉴权。
4. 仅安全方法免除 CSRF；管理后端所有 `POST`、`PUT`、`PATCH` 和 `DELETE` 请求统一受保护。
5. CSRF 拒绝由专用处理器返回统一 JSON。
6. 显式关闭 Spring Security 默认表单登录、HTTP Basic、默认退出端点和 CORS，避免产生第二套认证及跨域行为。

### 5.5 会话管理层

1. 登录保存用户前，如果请求已经存在 Session，则调用 `changeSessionId()`。
2. 请求没有 Session 时创建新 Session 并保存用户快照。
3. 退出时调用 `session.invalidate()`，确保整个 Session 与 Redis Session 数据失效。

## 6. CSRF 数据流

### 6.1 令牌初始化

1. 前端准备发送非安全方法请求。
2. 请求拦截器检查 `XSRF-TOKEN` Cookie。
3. Cookie 不存在时，通过不安装业务拦截器的独立 Axios 实例请求 `GET /api/csrf`。
4. 多个并发写请求共享一个初始化 Promise。
5. 后端访问 `CsrfToken` 以触发令牌生成并保存 Cookie，同时对该响应设置 `Cache-Control: no-store`。
6. 初始化成功后，原写请求继续发送；初始化失败时阻止写请求。

### 6.2 写请求校验

1. Axios 从 `XSRF-TOKEN` Cookie 读取令牌。
2. Axios 将令牌写入 `X-XSRF-TOKEN` Header。
3. Spring Security 在 Controller 前比较 Cookie 与 Header。
4. 令牌匹配时进入现有 Controller、Service 和 Mapper 链路。
5. 令牌缺失或不匹配时直接返回 `403`，业务代码不会执行。

### 6.3 登录和退出

1. 注册、登录和退出均属于写请求，必须通过 CSRF 校验。
2. 登录成功后轮换 Session ID，再写入登录用户。
3. 退出请求通过 CSRF 校验后销毁 Session，并确保身份 Cookie 失效。
4. CSRF Cookie 本身不代表登录身份；校验失败时由拒绝处理器使其过期，下一次写操作重新初始化。

## 7. Cookie 策略

### 7.1 Session Cookie

Session Cookie 使用独立名称 `FEIAPI_SESSION`，属性如下：

```text
HttpOnly=true
Secure=true（生产环境）
SameSite=Lax
Path=/api
Domain 不设置
```

本地 HTTP 开发环境将 `Secure` 设为 `false`。不设置 `Domain` 后，浏览器创建 Host-only Cookie，降低其他子域共享或覆盖会话 Cookie 的风险。

### 7.2 CSRF Cookie

CSRF Cookie 属性如下：

```text
名称=XSRF-TOKEN
HttpOnly=false
Secure=true（生产环境）
SameSite=Lax
Path=/
Domain 不设置
```

`HttpOnly=false` 是双提交 Cookie 方案的必要条件，因为 Axios 需要读取令牌并写入请求 Header。CSRF Cookie 不是身份凭据，真正的身份仍只由 `HttpOnly` Session Cookie 表示。

CSRF 令牌不得写入 `localStorage`、`sessionStorage`、Pinia、日志或业务 DTO。

## 8. CORS 策略

1. 删除管理后端硬编码的 `CorsConfig`。
2. 开发环境通过 Vite `/api` 代理访问管理后端。
3. 生产环境通过前端 Nginx `/api` 代理访问管理后端。
4. 管理后端不返回跨域凭据响应头。
5. SDK 客户端通过独立网关入口调用，不依赖浏览器 CORS。

CORS 不是身份认证、网络隔离或 CSRF 防护。即使未来重新开放 CORS，也不能移除本设计的 CSRF、Session 和端口隔离机制。

## 9. 浏览器安全响应头

前端 Nginx 使用公共配置片段，确保带有独立缓存 `add_header` 的各个 `location` 不会因 Nginx 继承规则丢失安全响应头。

基线响应头如下：

```text
Content-Security-Policy:
  default-src 'self';
  script-src 'self';
  style-src 'self' 'unsafe-inline';
  img-src 'self' data: https:;
  font-src 'self' data:;
  connect-src 'self';
  object-src 'none';
  base-uri 'self';
  frame-ancestors 'none';
  form-action 'self'

X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

当前页面存在静态内联样式，所以 `style-src` 暂时保留 `'unsafe-inline'`。`script-src` 不允许 `'unsafe-inline'` 或 `'unsafe-eval'`。外部头像只允许 HTTPS，前端现有头像 Origin 校验继续生效。

## 10. 错误处理

### 10.1 后端错误契约

CSRF 拒绝返回 HTTP `403` 和现有 `BaseResponse` JSON：

```json
{
  "code": 40300,
  "data": null,
  "message": "安全校验失败，请刷新页面后重试"
}
```

拒绝处理器只记录请求方法、URI 和必要的追踪信息，不记录 CSRF Token、Cookie、Session ID 或请求敏感数据。普通业务权限不足继续由现有全局异常处理链路响应，从而与 CSRF 拒绝保持职责分离。

### 10.2 前端处理

1. CSRF 初始化失败时拒绝本次写请求并显示固定安全提示。
2. 收到 CSRF `403` 时不自动重放写请求，避免删除、更新或提交操作重复执行。
3. 后端使失效的 CSRF Cookie 过期，下一次明确的用户操作重新初始化令牌。
4. 读取类页面不因 CSRF 初始化服务暂时不可用而整体失效。

## 11. 测试设计

### 11.1 后端测试

1. 无 CSRF 令牌的写请求返回 HTTP `403` 和业务码 `40300`。
2. 合法令牌写请求能够进入现有 Controller。
3. `GET`、`HEAD` 和 `OPTIONS` 等安全方法不要求令牌。
4. CSRF 初始化接口签发属性正确的 Cookie。
5. 生产配置下 Session Cookie 和 CSRF Cookie 均带 `Secure`，Session Cookie 带 `HttpOnly`。
6. Cookie 不包含 `Domain`，Session Cookie 的 `Path` 为 `/api`，CSRF Cookie 的 `Path` 为 `/`。
7. 登录前后 Session ID 不同，登录用户信息仍可读取。
8. 退出后原 Session 不再有效。
9. 非同源请求不会收到 `Access-Control-Allow-Origin`。
10. CSRF 拒绝日志和响应不包含令牌或会话信息。

现有以下 8 个测试类中的 107 个 MockMvc 写请求补充 Spring Security Test 的合法 CSRF，不在测试 Profile 中关闭 CSRF：

- `UserSmokeTest.java`
- `InterfaceInvokeSmokeTest.java`
- `InterfaceDocControllerTest.java`
- `UserInterfaceInfoControllerTest.java`
- `AnalysisControllerTest.java`
- `UserControllerTest.java`
- `InterfaceInfoControllerTest.java`
- `AuthInterceptorTest.java`

### 11.2 前端测试

1. 安全方法不会触发 CSRF 初始化。
2. 首个写请求会先获取令牌。
3. 多个并发写请求只产生一个初始化请求。
4. 已有 Cookie 时不重复初始化。
5. 初始化失败时原写请求不发送。
6. CSRF `403` 被转换为固定提示且不会自动重试。
7. HTTP 成功解包和普通业务错误契约保持不变。

### 11.3 部署验证

1. `nginx -t` 通过。
2. `/`、`/index.html`、静态资源、SPA 回退和 API 代理响应均包含预期安全响应头。
3. CSP 不阻止生产脚本、样式、字体、图片和同源 API 请求。
4. `docker compose config` 显示后端没有宿主机端口，前端与网关只绑定回环地址。
5. 外部无法通过服务器 IP 的 `9527` 或 `8090` 原始端口绕过边缘代理。

### 11.4 质量门禁

1. 后端执行全量 Maven 测试。
2. 前端执行类型检查、全量测试、覆盖率和生产构建。
3. 新增核心安全模块行覆盖率不低于 90%。
4. 构建后的 Nginx 镜像执行配置检查和响应头验证。

## 12. 预计改动文件

### 12.1 后端仓库

- `feiapi-backend-server/pom.xml`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/config/SecurityConfig.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/config/CorsConfig.java`（删除）
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/controller/CsrfController.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/security/CsrfAccessDeniedHandler.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/component/UserSessionManager.java`
- `feiapi-backend-server/src/main/resources/application.yml`
- `feiapi-backend-server/src/main/resources/application-prod.yml`
- CSRF、安全配置、会话管理及现有 MockMvc 测试文件

### 12.2 前端仓库

- `src/services/http.ts`
- `src/services/__tests__/http.test.ts`
- `vite.config.ts`
- `nginx.conf`
- `nginx/security-headers.conf`
- `Dockerfile`
- `src/env.d.ts`

### 12.3 部署仓库

- `docker-compose.yml`
- `README.md`
- `.env.example`

### 12.4 根目录文档

- `doc/前端代码架构审查报告.md`
- `doc/环境变量配置说明.md`

根目录文档不属于当前任一 Git 仓库，实施完成后直接更新，并在交付说明中单独列出。

## 13. Git 分支策略

1. 后端和前端分别从现有 `dev` 创建临时 `feature` 分支。
2. 部署仓库当前只有 `main`，先从 `main` 创建长期 `dev`，再从 `dev` 创建临时 `feature` 分支。
3. 各仓库完成验证后使用中文提交说明并保留 `feat`、`fix` 或 `test` 前缀。
4. 临时 `feature` 分支合并到对应 `dev` 后删除，不直接合并到 `main`。

## 14. 验收标准

1. 浏览器管理请求在开发和生产环境均只访问当前 Origin 的 `/api`。
2. 任一管理后端写请求缺少或伪造 CSRF 令牌时均在业务执行前被拒绝。
3. 合法前端写请求无需业务页面手动管理令牌。
4. 登录轮换 Session ID，退出销毁 Session。
5. Session Cookie 和 CSRF Cookie 属性符合本设计，且均为 Host-only Cookie。
6. 管理后端不再启用跨域凭据访问。
7. 公网无法绕过边缘代理直接访问管理后端原始端口。
8. SDK 仍可通过独立受控域名访问网关。
9. 前端页面和静态资源包含设计规定的安全响应头，生产资源在 CSP 下正常加载。
10. 后端全量测试、前端类型检查、全量测试、覆盖率、生产构建及部署配置验证全部通过。
