# Delivery Glance Core 技术架构研究

研究日期：2026-08-09

> **当前实现范围（2026-08-09）：** 本文保留完整架构论证，但 [Ticket 12](../issues/12-rescope-to-resume-ready-core.md) 已缩小 Core。实现 Agent 只采用单体、PostgreSQL/JdbcClient、最新位置内存存储、安全 Link、Recipient SSE 与测试/容器基础；ETA/地址 provider 属于 [Future Work 13](../issues/13-add-travel-time-eta.md)，Redis/PostGIS/WebFlux/扩展观测属于 [Future Work 18](../issues/18-run-measured-scale-and-resilience-experiment.md)，Kafka 属于 [Future Work 19](../issues/19-evaluate-durable-domain-event-backbone.md)。当前可执行栈以 [Technical baseline](../implementation/TECHNICAL-BASELINE.md) 为准，不要把本文每个完整产品建议都变成依赖。

本报告只使用官方文档、标准和官方项目文档作为外部事实来源。项目约束来自已锁定的 [Core/Expansion 边界](../issues/07-set-core-and-expansion-boundaries.md)、[Tracking Link 生命周期](../issues/06-define-tracking-link-lifecycle.md) 与 [Courier Location 数据最小化规则](../issues/11-define-courier-location-reporting-and-retention.md)。凡是从事实推导到本项目做法的地方，均明确标为“架构推论”；它不是来源原文，也不改变产品范围。

## 完整架构研究的原结论摘要（当前 Core 只采用上方子集）

原完整范围建议一个同源部署、单实例、单数据库的模块化单体；Ticket 12 继续采用这个基础形状，但不采用下表中的 ETA/provider 或扩展组件作为当前 Core 工作：

| 层 | Core 选择 | 不在 Core 引入 |
|---|---|---|
| 后端 | Java 25 LTS、Spring Boot 4.1.0、Spring MVC | WebFlux、微服务 |
| 前端 | React 19.2、TypeScript、Vite 8.1；生产静态产物由 Boot 同源提供 | SSR/Next.js、独立前端部署 |
| 持久化 | PostgreSQL 18 当前 minor、Flyway、显式 SQL/JdbcClient | 第二个 durable store、应用内唯一性判断 |
| 实时 | 普通 HTTP JSON 处理命令；`SseEmitter` 处理 server-to-browser read updates | WebSocket、Kafka 位置流 |
| 位置 | 单实例内存中的每 Courier 一个 latest snapshot；PostgreSQL 只存无坐标 session/audit | Route History、原始 ping 表、坐标日志/trace/backup |
| 安全 | Spring Security server-side session cookie + CSRF；Recipient fragment capability 兑换受限 session | 自制 JWT、浏览器持久化 bearer token |
| 地图/地址/ETA | 当前 Core 只本地打包 MapLibre 并由人工输入坐标；有许可的商业 tile provider 是发布输入；Mapbox geocoding/Directions adapter 属于 Future Work 13 | OSMF 公共 Nominatim/公共 tile 作为生产依赖；Core 自建 OSRM/Nominatim |
| 运维 | Actuator、Micrometer、结构化脱敏日志、Flyway、Testcontainers PostgreSQL | 为了“看起来完整”而先搭 Collector、Kafka、Redis、PostGIS |

建议的运行拓扑是：

```text
Dispatcher / Courier / Recipient browser
                 │ HTTPS, same origin
                 ▼
       one Spring Boot application
       ├─ REST command/query endpoints
       ├─ Spring MVC SSE registry
       ├─ in-process latest-location store
       ├─ scheduler: round close / expiry / ETA due
       └─ provider ports: geocode / travel time
                 │
                 ▼
          one PostgreSQL database
       durable Delivery/Assignment truth

Recipient browser ──direct tile requests──► licensed tile CDN
Boot server ──minimal coordinate request──► geocode / travel-time API
```

这套拓扑故意让实时消息成为“刷新提示”，而不是事实来源：所有 durable 业务结果先在 PostgreSQL 事务中提交；SSE 丢失或进程重启后，客户端重新读取当前权威 snapshot。Current Location 是唯一例外——它按产品决定就是可丢失的 ephemeral latest value，服务重启后应显示 Unavailable，等待新报告，而不是从历史重放。

## 1. 当前可支持的版本基线

### Java 与 Spring

- Oracle 当前路线图列出 Java 8、11、17、21、25 为 LTS；Java 25 于 2025-09 发布，Premier Support 至 2030-09、Extended Support 至 2033-09。Java 26 是非 LTS；Oracle 还明确提示 Java 21 的 permissive-license overlap 将在 2026-09 后结束。[Oracle Java SE Support Roadmap](https://www.oracle.com/uk/java/technologies/java-se-support-roadmap.html)
- Spring 官方项目页当前稳定版是 Boot 4.1.0；Boot 4.1.0 要求 Java 至少 17、兼容至 26，并要求 Spring Framework 7.0.8+。同一官方文档也列出稳定线 4.0.7、3.5.16、3.4.13、3.3.13。[Spring Boot project](https://spring.io/projects/spring-boot/)、[Boot 4.1 system requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html)
- Boot 3.5.16 同样要求 Java 至少 17、兼容至 Java 25，并要求 Spring Framework 6.2.19+；它是保守兼容线，而不是当前最新线。[Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- Spring Boot 官方支持政策是 major 至少三年、但必须运行受支持的 minor；minor 至少十二个月，并建议迁到最新受支持 release。[Spring Boot Supported Versions](https://github.com/spring-projects/spring-boot/wiki/Supported-Versions)

**架构推论：** 新项目锁定 Java 25 LTS + Boot 4.1.0，并由 Boot BOM 管理 Spring/第三方传递依赖；CI 和容器使用同一 Java 25 distribution。Boot 3.5.16 只保留为“某个必要依赖尚不兼容 Boot 4”的有证据 fallback，不因熟悉度默认退回。Java 26 的新特性不足以抵消非 LTS 的升级窗口。

### PostgreSQL

PostgreSQL 官方支持一个 major 五年，并建议始终运行该 major 的当前 minor。截至研究日，PostgreSQL 18.4 是当前 18 minor，支持至 2030-11-14；17.10、16.14、15.18、14.23 也仍受支持。[PostgreSQL Versioning Policy](https://www.postgresql.org/support/versioning/)

**架构推论：** 新数据库用 PostgreSQL 18 的当前 minor。若低成本托管平台只提供 17，则 17 的当前 minor 是可辩护兼容选择；不能因此改变 SQL 一致性设计。生产、Testcontainers 和迁移验证应尽量使用同一 major。

### React、TypeScript、Vite 与 Node

- React 官方 versions 页面当前为 React 19.2，并列出 19.2.7 为 2026-06 release。[React Versions](https://react.dev/versions)
- React 已弃用 Create React App，并明确把 Vite 列为从零构建 React app 的 build-tool 方向之一。[React: Sunsetting Create React App](https://react.dev/blog/2025/02/14/sunsetting-create-react-app)
- Vite 8.1 于 2026-06-23 发布；Vite 8 要求 Node 20.19+ 或 22.12+。[Vite 8.1 release](https://vite.dev/blog/announcing-vite8-1)、[Vite 8 release](https://vite.dev/blog/announcing-vite8)
- Node 官方页面显示 Node 20 已 EOL，Node 24 是 LTS，而 Node 26 仍是 Current；官方建议生产只用 Active LTS 或 Maintenance LTS。[Node.js Releases](https://nodejs.org/en/about/previous-releases)

**架构推论：** 锁定 React 19.2 当前 patch、TypeScript、Vite 8.1 和 Node 24 LTS。使用 Vite `react-ts` 模板；CI 单独运行 `tsc --noEmit`，因为 Vite 官方明确说明它只 transpile TypeScript、不做 type checking。[Vite Features: TypeScript](https://vite.dev/guide/features.html#typescript) 一个 client-side SPA 足够，生产 build 放入 Boot static resources（Boot 会从 static content locations 提供 `index.html`），从而维持同源 cookie、API 和 SSE；不为这个内部工作区和 no-index Recipient 页面增加 SSR server。[Spring Boot Servlet Web Applications](https://docs.spring.io/spring-boot/reference/web/servlet.html) 路由使用 browser history 并由 Boot 对非 API routes 回退 `index.html`；不能用 hash router，因为 URL fragment 已专用于 bootstrap capability，兑换后还要被 `replaceState` 清除。

## 2. 模块化单体边界与数据所有权

建议用一个 Gradle/Maven build、一个 executable jar、一个 Docker image，但按业务能力分顶层 package：

```text
com.deliveryglance
├── identityaccess   # internal account, role, session-facing policies
├── delivery         # lifecycle and guarded transitions
├── matching         # recommendation, round, interest, atomic selection
├── trackinglink     # capability metadata, derivation, redemption, revocation
├── location         # sharing session + ephemeral latest snapshot interface
├── eta              # provider port, cadence, current ETA projection
├── recipientview    # privacy-reduced read model and SSE subscriptions
└── shared           # tiny technical primitives only; no dumping-ground domain
```

每个 package 只公开 application facade、command/query DTO 和必要的 domain event；controller、repository、entity/row mapper 留在模块内部。跨模块的业务状态改变走同步 facade，并共享一个 PostgreSQL transaction；事务提交后才发布只用于刷新 view 的进程内 notification。

Spring Modulith 官方提供 application-module 模型以及 `ApplicationModules.verify()` 对循环和 internal-package 访问的验证能力。[Spring Modulith fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)、[Spring Modulith verification](https://docs.spring.io/spring-modulith/reference/verification.html)

**架构推论：** Core 最多采用一条 Modulith architecture verification test，不采用其持久事件注册表或把 build 拆成多个 artifact。这里的“模块化”是可验证的代码所有权，不是提前拆服务。单 deployable 保留跨 Delivery/Matching/Assignment 的本地事务，也把 48 小时基础设施工作量压到最低。

## 3. PostgreSQL 是 durable truth，也是 Assignment 的最终并发防线

### 数据库能提供的保证

- PostgreSQL 的 `SELECT ... FOR UPDATE` 会锁定选中行，阻止并发更新/删除或取得冲突 row lock，直至当前 transaction 结束。[PostgreSQL `SELECT`](https://www.postgresql.org/docs/current/sql-select.html)、[Explicit Locking](https://www.postgresql.org/docs/current/explicit-locking.html)
- 普通 `UNIQUE` 约束覆盖所有行；若唯一性只适用于一部分行，PostgreSQL 官方明确要求使用 unique partial index。[PostgreSQL Constraints](https://www.postgresql.org/docs/18/ddl-constraints.html)
- Serializable 可以保证成功提交的 transaction 等价于某个串行顺序，但应用必须能重试整个 transaction；serialization failure 使用 SQLSTATE `40001`。[PostgreSQL Transaction Isolation](https://www.postgresql.org/docs/current/transaction-iso.html)

### 推荐的原子 selection transaction

**架构推论：** Matching Round 到期时，执行一个短 transaction：

1. 锁定 `matching_round` 与 `delivery`；若 round 已关闭或 Delivery 已不再 Awaiting Assignment，幂等返回既有结果。
2. 按 Courier ID 稳定排序后 `FOR UPDATE` 锁定本轮 Interested candidates 的 Courier rows，避免两个 overlapping rounds 以相反顺序锁人。
3. 在锁内重新验证 On Duty、active sharing-session generation、Service Zone、无 Active Delivery，以及内存 latest snapshot 的 freshness/accuracy；按已锁定的 rank policy 重排。
4. 插入唯一 Active Assignment、更新 Delivery/Courier/round outcome、写 Recommendation Decision 与 coordinate-free audit，然后一次 commit。
5. commit 后才发送 `DeliveryViewChanged`/`AssignmentChanged` 内存通知；通知失败不回滚已提交事实。

至少建立以下 database-level guard，而不是只写 `if`：

```sql
CREATE UNIQUE INDEX uq_active_assignment_delivery
  ON assignment(delivery_id) WHERE ended_at IS NULL;

CREATE UNIQUE INDEX uq_active_assignment_courier
  ON assignment(courier_id) WHERE ended_at IS NULL;
```

Matching interest/reservation 也应以与产品 policy 相符的 unique/partial unique index 收口。Assignment insert 可用 `INSERT ... ON CONFLICT DO NOTHING RETURNING ...`，零行表示并发 race 已由另一 transaction 赢得；PostgreSQL 官方把 `ON CONFLICT` 定义为 unique/exclusion conflict 的替代动作，并保证 `DO UPDATE` 的原子 insert-or-update outcome。[PostgreSQL `INSERT`](https://www.postgresql.org/docs/18/sql-insert.html) 应用随后重新验证并尝试下一 Eligible candidate或得出 no assignment。若采用会抛 unique/serialization/deadlock exception 的路径，则必须 rollback 并从 transaction 边界重试，不能在已 aborted transaction 中继续，更不能吞掉错误后声称成功。

显式 row locks + partial unique indexes 已能覆盖当前选择路径；不必把整个应用默认设为 Serializable。对无法通过固定行集合锁定的新增跨行 invariant，才在局部 transaction 使用 Serializable 并实现完整 retry。无论隔离级别如何，唯一索引仍是最后一道不可绕过的防线。

### 数据访问技术

**架构推论：** 对这个项目，Spring `JdbcClient`/named-parameter JDBC + 手写 PostgreSQL SQL 比用 ORM 隐藏 `FOR UPDATE`、partial index 和 conditional transition 更容易审计。CRUD mapping 可以保持简单；Flyway 拥有 schema，应用启动只验证/使用 schema，不让 ORM 自动建表。若团队最终选 JPA，也必须把上述锁与索引留在显式 repository SQL/Flyway 中；技术选择不能削弱 database invariant。

## 4. Current Location：只保存一个内存 snapshot

### 浏览器实际能承诺什么

- W3C Geolocation API 只暴露在 Secure Context，要求用户明确授权；`watchPosition()` 可提供重复更新，并返回 coordinates、accuracy 与 timestamp。当前规范还规定，Document 不 fully active 时失败，位置请求只在 Document visible 时继续。[W3C Geolocation](https://www.w3.org/TR/geolocation/)
- Chrome 官方 Page Lifecycle 文档指出 hidden 往往是移动端最后一个可靠可观察状态；页面可无事件进入 discarded，frozen 状态会暂停 timer 和 fetch callback，`unload` 不可靠且不推荐。[Chrome Page Lifecycle](https://developer.chrome.com/docs/web-platform/page-lifecycle-api)

**架构推论：** Courier 前端只能把十秒 cadence 当目标：显式 Start 后调用 `watchPosition`，只在 `document.visibilityState === "visible"` 时选取 newest reading 发送；hidden 时停止发送并标为 interrupted，返回同一尚未 reload 的页面时恢复。不要用 service worker、offline queue、`unload` 或 timer 伪装后台追踪。HTTPS 是功能前提而不只是部署加固。

### Core store contract

**架构推论：** 实现一个 `LatestLocationStore` port，Core adapter 是单实例内存 `ConcurrentHashMap<CourierId, Snapshot>`。每个 snapshot 只有：

```text
lat, lon, accuracyMetres, recordedAt, receivedAt,
sharingSessionId, sharingSessionGeneration
```

更新必须是一次原子 compare-and-replace：先验证 session/purpose、未来时间、两分钟 age、100m accuracy；再按 `recordedAt` 更新，时间相同时只允许更小 accuracy 替换。请求体通过验证后不再被任何队列、log、trace 或 event 保存。每 Courier 的 generation 让迟到的旧 session report 即使与 Stop/Restart 交错也不能复活位置。

Stop、permission revoked、sign-out、无 collection purpose 和两分钟 expiry 都调用同一个 idempotent `removeIfGenerationMatches`；SSE view 在删除后收到“location unavailable”，而不是最后坐标。定时 cleanup 是兜底，read path 自身也必须按 `recordedAt` 判 stale，不能依赖 cleanup 准点执行。

PostgreSQL 只保存 coordinate-free `LocationSharingSession(startedAt, endedAt, reason, generation)` 和当前解释状态；服务进程崩溃会丢失坐标，这正符合已锁定产品行为。重新启动后 Recipient/Dispatcher 显示 Unavailable，Courier 的仍开页面只有提交一条新可用 reading 后才恢复。数据库 backup、Kafka topic 和可观测性系统中没有 raw coordinate。

单实例下，location report、explicit Stop 和 matching selection 对同一 Courier 使用同一 keyed mutex/striped lock；selection 在取得该 lock 后再进入上述 DB transaction，并要求内存 snapshot generation 等于锁内 durable sharing-session generation。这样不需要把坐标写入 durable DB，也不会让旧 session snapshot 穿过 Stop。

## 5. HTTP writes + SSE read updates

### 官方能力与浏览器语义

- Spring MVC 原生支持 Servlet async processing、流式多值 response 和 `SseEmitter`。Servlet API 不会可靠通知应用远端已经断开，Spring 官方因此建议周期发送 heartbeat/comment；MVC streaming write 是 blocking write，默认 async executor 不适合生产负载，必须显式配置。[Spring MVC Asynchronous Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
- WHATWG SSE 规范规定 EventSource 在连接关闭后重连，HTTP 204 可停止重连；server 发出的 `id` 会在重连时成为 `Last-Event-ID`，`retry` 可修改重连延迟。规范还建议约十五秒发 comment，避免 legacy proxy 丢掉空闲连接。[WHATWG Server-Sent Events](https://html.spec.whatwg.org/dev/server-sent-events.html)
- Spring 官方对 MVC/WebFlux 的选择建议很直接：若使用 JDBC/JPA 或其他 blocking persistence，常见架构应选 MVC；WebFlux 的主要收益是用少量固定 event-loop threads 承载高并发 non-blocking I/O，而 reactive/non-blocking 本身通常不会让单次请求更快，且转换需要更多工作。[Spring WebFlux overview](https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html)

### 推荐协议

**架构推论：** 所有命令仍是普通 authenticated HTTP `POST/PATCH/DELETE`，带 idempotency key/version precondition；SSE 只承担 Recipient、Dispatcher、Courier read-view invalidation/snapshot update，永远不承担命令或 Assignment ownership。

每个 SSE connect/reconnect：

1. 先验证 session/link generation/expiry/revocation；
2. 注册 emitter 时处理“读 snapshot 与订阅之间”的竞态；注册后再读一次或在同一 sequence gate 下读；
3. 立即发送完整、privacy-filtered authoritative view；
4. 后续发送带 `id: <delivery-view-version>` 的完整 view 或小型 invalidation；
5. 约十五秒发送 comment heartbeat，并在 completion/error/timeout 清理 registry。

`Last-Event-ID` 用于判断客户端可能落后，不把进程内 events 做成 durable replay log。EventSource 的自动重连不是“不丢事件”保证；重连时读取当前 snapshot 才是恢复机制。Delivery version 来自 durable state；位置 version 只在进程内有效，重启后位置自然 Unavailable。

当前验收仅 100 个 SSE connections、最多约 100 个 On Duty Couriers 每十秒一个 report（约 10 writes/s）。这是采用 MVC 的容量假设，不是无测试保证；必须用真实浏览器/HTTP client 做十五分钟 gate，观察 active connections、async executor、Tomcat threads、disconnect cleanup 和 accepted-location-to-render p95。未失败前没有 WebFlux 迁移动机。

## 6. 同源 server-side session，而不是 JWT

### Spring Security 能直接提供的部分

- Spring Security 默认可以在 `HttpSession` 中持久化 authentication，并在 login 时自动更换 session ID 以防 session fixation；Servlet 3.1+ 默认使用 `changeSessionId()`。[Spring Security Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- Spring Security 对 unsafe methods 默认开启 CSRF；默认 expected token 可在 `HttpSession`，并提供 `.csrf(csrf -> csrf.spa())` 的 SPA 配置。登录和退出会清除旧 CSRF token，SPA 必须重新取得 token。[Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- Spring Boot 可通过 `server.servlet.session.cookie.same-site` 配置 session cookie 的 SameSite。[Spring Boot Servlet Web Applications](https://docs.spring.io/spring-boot/reference/web/servlet.html)
- JWT 是携带 claims 的 URL-safe token；其安全 BCP 要求严格验证 algorithm、cryptographic input、issuer、audience、token type 等，并记录了多类真实错误实现攻击面。[RFC 7519](https://www.rfc-editor.org/rfc/rfc7519.html)、[RFC 8725](https://www.rfc-editor.org/rfc/rfc8725.html)

### 比较与选择

| 关注点 | server-side session cookie | JWT |
|---|---|---|
| 单同源 SPA | 浏览器自动发送 opaque session ID；无需 CORS/token storage | 仍需决定 token storage、refresh、logout 与浏览器发送方式 |
| 即时 logout/撤权 | 删除/失效 server session 即可 | `exp` 之前仍需 deny-list、generation/version lookup 或短 TTL |
| Tracking Link rotation | session 中保存 `linkId + generation`，每次 read/SSE connect 验证 | 为即时 rotation 仍必须查 server state，失去“无状态”收益 |
| CSRF | cookie 是 ambient authority，保留 Spring CSRF | Authorization header 可避开典型 CSRF，但 JS-readable token 增加泄漏面；cookie JWT 仍要 CSRF |
| 实现量 | Spring Security 已提供成熟默认 | 额外 key/claim/algorithm/audience/refresh 设计 |

**架构推论：** 单实例、同源 SPA 选 server-side opaque session。内部 Dispatcher/Courier 采用预 provisioned account + role authorization；cookie 设置 `Secure; HttpOnly; SameSite=Lax`、host-only、有限 timeout，登录后换 ID。所有 unsafe API 使用 CSRF header，不全局关闭 CSRF；SPA 初始化以及 login/logout 后重新取得 token。

Recipient redemption 把 tracking grant（`linkId + generation + expiry`）放入同一个 server-side session 的窄权限集合，只允许 Recipient read endpoints；它不是 Dispatcher/Courier principal。每个 Recipient API、SSE connect，以及 heartbeat/event 前都检查 current generation/expiry。Rotation/revocation transaction commit 后主动关闭该 link 的本进程 emitters；即使 close notification 异常，下一 heartbeat/read 也会拒绝。

JWT 在这里既不能消除 PostgreSQL revocation check，也不能帮助原生 EventSource 添加 authorization header，因此 Core 拒绝。将来只有出现多个独立 resource servers 需要离线验证这一新需求时，才重新评估 OAuth/JWT；“多实例”本身也不充分，因为共享 server-side session 可用数据库/专用 session store。

## 7. 可重复 Copy、但不持久化 raw capability 的 HMAC 派生

### 可依赖的密码学 primitive

- Java 25 的 `Mac` API 保证平台实现支持 `HmacSHA256`；HMAC 以 secret shared key 配合 SHA-256 等 hash 工作。[Java 25 `Mac`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Mac.html)
- Java `SecureRandom` 被定义为 cryptographically strong RNG；Java `MessageDigest` 提供 SHA-256 one-way digest，且 `MessageDigest.isEqual` 提供内容无关的 fixed-length digest comparison。[Java 25 `SecureRandom`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html)、[Java 25 `MessageDigest`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html)
- NIST SP 800-108 Rev.1 规定以 HMAC、CMAC、KMAC 这些 PRF 从 secret key 派生 keying material；label/context 用于区分派生用途。[NIST SP 800-108 Rev.1](https://csrc.nist.gov/pubs/sp/800/108/r1/final)
- RFC 2104 定义 HMAC keyed hashing 并要求 key 具备强随机性；Java 的 URL encoder 可产生 RFC 4648 URL/file-safe Base64。[RFC 2104](https://www.rfc-editor.org/rfc/rfc2104.html)、[Java 25 `Base64`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Base64.html)

### 建议机制（明确是架构推论）

产品要求“Copy current link 每次得到同一链接”，所以纯随机 raw token 只在首次返回、以后不可恢复的方案不满足要求；把 raw token 加密后存 DB 又扩大了 secret persistence。可以用版本化 server secret 把同一内部标识确定性映射为同一 256-bit capability：

```text
input = fixedBinaryEncoding(
  "delivery-glance/tracking-capability/v1",
  keyVersion,
  randomInternalLinkId,       // 128-bit internal identifier, not authorization
  generation                 // unsigned 64-bit / fixed-width
)

rawTokenBytes = HMAC-SHA-256(derivationKey[keyVersion], input)  // full 32 bytes
urlToken      = Base64urlWithoutPadding(rawTokenBytes)           // 43 chars
verifier      = SHA-256(rawTokenBytes)
```

数据库只保存：

```text
link_id, delivery_id, generation, key_version,
verifier UNIQUE, issued_at, expires_at, revoked_at, lifecycle metadata
```

`derivationKey[keyVersion]` 是至少 256-bit 的 secret，放部署 secret manager/environment injection，不进数据库、repository、image、log 或 telemetry。`input` 必须 fixed-width 或 length-prefixed，不能用有歧义的字符串拼接；固定 domain label 防止同一 key 被另一个 token 类型复用。不要截短 32-byte HMAC output。

流程如下：

- **首次签发与再次 Copy：** authenticated Dispatcher endpoint 读取 current row，按 `keyVersion + linkId + generation` 重新计算 token，再核对其 SHA-256 是否等于 stored verifier；因此每次 Copy 得到完全相同的 `https://.../track#t=<token>`，但 raw token 从未落库。
- **Recipient 兑换：** bootstrap JS 在加载第三方资源前读取 fragment，以同源 CSRF-protected JSON POST 发送 token。server Base64url decode、限制为恰好 32 bytes、计算 SHA-256 并用 indexed verifier 查 current row，再重新派生并 constant-time compare；成功后建立窄权限 session，立即 `history.replaceState` 清掉 fragment。
- **Rotation/Reissue：** 增加 generation（并在需要时切换 key version）、生成新 verifier，在同一 transaction 撤销旧 generation。旧 token digest 再也匹配不到 current row；所有旧 derived sessions/SSE 依据 generation 失效。
- **计划 key rotation：** key ring 保留仍有 active links 的旧版本，以继续 Copy 同一个 current link；等这些 links 全部到期/轮换后删除旧 key。若是 key compromise，则不能为“保持同链接”保留已泄漏 key，必须批量 revoke/reissue，并形成审计。

这里使用 HMAC 作为确定性 PRF 是基于 JCA/NIST primitive 的项目设计推论，并不是 NIST 对 Delivery Link 的直接规范。实现必须有固定 test vectors（相同 input/key 恒等、generation/domain/key 改变则不同）、key-version rotation test、DB dump 不含 raw token test、日志扫描、旧 generation 即时失效以及“Copy 两次字节完全相同”的验收。

这个机制不能让已经复制到消息、剪贴板或 Recipient 设备的 bearer secret 消失；“不持久化 raw capability”只约束 Delivery Glance 的 server/database/log/telemetry。链接仍须遵守既定 fragment bootstrap、`Cache-Control: no-store`、`Referrer-Policy: no-referrer`、无第三方 analytics/script 和统一 unavailable response。

## 8. 地图、地址解析与旅行时间

应把三件事拆成三个 port，因为“画地图”“address → durable point”“point pair → travel seconds”具有不同许可、隐私和失效语义。

### Core 默认组合

#### 地图渲染：本地 bundle MapLibre + 有许可的 tile service

MapLibre GL JS 是开源 TypeScript/browser WebGL vector-tile renderer；它是 renderer，不附带生产地图数据。[MapLibre GL JS](https://maplibre.org/maplibre-gl-js/docs)

**架构推论：** 把 `maplibre-gl` 随 Vite bundle 一起部署，避免 Tracking 页面运行第三方 JavaScript；只把 style/tile/font hosts 加入 CSP。首个 demo adapter 可用 MapTiler Cloud 或另一家明确许可的 OSM-derived tile CDN，但必须显示供应商/OSM attribution，并使用允许当前公开 portfolio/demo 的 plan。

MapTiler 条款目前规定 free plan 只限 non-commercial 与 commercial R&D；通常要求 end-user 直接请求其 CDN，server proxy 需额外许可，map content 的 server-side cache 被禁止。[MapTiler Cloud Terms](https://www.maptiler.com/terms/cloud/) 这意味着 tile provider 会看到 Recipient IP、请求时间和 viewport 涉及的 tile area——这是从 direct-request contract 得出的隐私推论，必须写入 privacy notice。不要把 tracking token、Delivery ID 或精确 marker coordinate 放进 tile URL；fragment 已先被清除。

OSMF 的公共 `tile.openstreetmap.org` 不能作为本项目生产默认：其官方 policy 是 best-effort、无 SLA，并要求 web request 发送有效 Referer，明确禁止 restrictive `Referrer-Policy`，还要求不要提交 personal/confidential data。[OSM Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/) 这与 Tracking 页面锁定的 `no-referrer` 安全头直接冲突。OSM data 可用不等于社区 tile servers 是免费生产 CDN。

#### 地址解析：Mapbox Permanent Geocoding reference adapter

Mapbox Geocoding v6 官方区分 Temporary 与 Permanent：Temporary 结果不可缓存；Permanent 结果可无限期保存，但需要有效信用卡或 enterprise contract，并显式 `permanent=true`。[Mapbox Geocoding API](https://docs.mapbox.com/api/search/geocoding/)

**架构推论：** Delivery 的 pickup/handoff coordinates 要跨 session 保存并参与后续 matching/ETA，因此 reference adapter 必须使用有永久存储权的请求；默认 Mapbox Permanent 比“先临时 geocode 再悄悄落库”更可辩护。请求由 Boot server 发出，地址不进入浏览器第三方 SDK；Dispatcher 必须从候选中确认，保存 user-entered address、provider、provider result ID/point、confirmedAt。API key 只在 server secret 中。

Google Geocoding 是可替换选项，但当前官方 policy 规定 caching/storage 一般受限（place ID 可无限保存），结果若显示在地图上通常须显示在 Google Map，并要求公开 ToU/privacy；EEA billing address 还有单独条款。[Google Geocoding Policies](https://developers.google.com/maps/documentation/geocoding/policies) 因此不能把 Google geocode 与 MapLibre 任意混搭后假设合规；只有确认合同、存储和 display 权利后才能换。

OSMF 公共 Nominatim 明确限制总量约 1 request/s、禁止 autocomplete，并点名 package/vehicle tracking applications 等 primary-geocoding services 必须自建；同时要求不要提交 personal/confidential material。[Nominatim Usage Policy](https://operations.osmfoundation.org/policies/nominatim/) 所以它不是 Delivery address 的 Core 免费后端。自建 Nominatim 是未来运营项目，不是 48 小时实现。

#### ETA：Mapbox Directions reference adapter，Google Routes 备选

Mapbox Directions 的 `driving-traffic` profile 使用当前与历史交通，返回 seconds duration；官方 limit 是 300 requests/minute，并按 request 计费。[Mapbox Directions API](https://docs.mapbox.com/api/navigation/directions/)

Google Routes `Compute Routes`/`Compute Route Matrix` 返回 route、distance 和 travel time，并可使用 real-time traffic。[Google Routes API](https://developers.google.com/maps/documentation/routes)

**架构推论：** Core 先实现 Mapbox Directions adapter，因为同一 vendor 可覆盖永久 geocode 与 traffic-aware travel time；但 domain 只接收 `TravelEstimate(duration, distance, calculatedAt, providerStatus)`，不能接触 Mapbox DTO。Boot server 每次只发 origin/destination/mode，要求 `overview=false`/仅 duration+distance，不请求或保存 polyline。不要发送 Courier/Delivery/Recipient ID，并关闭/脱敏 HTTP client 的 URL/body debug logging；provider 仍会获得精确 origin/destination 和 Delivery Glance account/server IP，必须在 provider review/DPA/privacy notice 中承认。Mapbox 自己的 privacy notice 也把 IP、network activity 和 latitude/longitude 列为其可能处理的数据类别。[Mapbox Privacy](https://www.mapbox.com/legal/privacy)

已锁定 cadence 是 assignment/pickup 以及 endpoint 移动至少 5m 后每分钟最多一次，不是每个十秒 location ping 都调用 provider。Core 最多 50 Active Deliveries，正常上界约 50 requests/minute，低于 Mapbox 当前 300 rpm；仍需 timeout、bulkhead、rate budget、jitter、metrics 和项目既定“短暂保留旧 ETA 后 Unavailable”行为。Expansion 的 1,000 Active Deliveries 理论上可达 1,000 rpm，不能假设相同 quota 足够；届时需取得 quota/contract、batch/matrix 或更换 provider，并重新跑 failure/load gate。

开源 OSRM 可 self-host route/table HTTP service，并返回 fastest-route duration（seconds）和 distance（metres）。[OSRM HTTP API](https://project-osrm.org/docs/) **架构推论：** 自建它还要运营 OSM extract、profile、preprocessing、数据更新和 routing service，因此是成本/数据驻留产生证据后的 Expansion substitution，不是 Core 依赖；其输出仍必须经过同一 ETA port 和 unavailable semantics。

### Provider safety gate

在写实现前必须完成一页 provider record：允许存什么、保存多久、是否允许与当前 renderer 混用、attribution、最终用户隐私披露、EEA/DPA、quota、计费 cap、API key restriction、故障 status page。官方条款会变，部署时要复核；代码中的 provider adapter 不能被当成法律结论。

## 9. 数据库迁移、可观测性与测试

### Flyway

Spring Boot 官方建议 schema generation 只使用一种机制；Boot 4.1 可用 `spring-boot-starter-flyway`，PostgreSQL 另加 `flyway-database-postgresql`，默认从 `classpath:db/migration` 运行 `V...__....sql`。[Spring Boot Database Initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html) Flyway versioned migrations 按顺序只应用一次，并把 checksum 存进 schema history table；已进入 permanent environment 的 migration 不应改写，应新增 forward migration。[Flyway Versioned Migrations](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html)

**架构推论：** V1 起所有 table/index/partial unique/check/foreign key 都进 Flyway；本地、CI、生产运行相同 migrations。不要同时用 `schema.sql`、Hibernate `update/create`。若使用 JPA，设 `ddl-auto=validate`；若使用 JdbcClient，则以 Flyway validation、Testcontainers repository tests 和启动 smoke query 覆盖 schema contract，不能假设 JDBC 会自动验证 row mapping。

### Micrometer 与 OpenTelemetry

- Boot Actuator 自动配置 Micrometer，并能提供 JVM、system、HTTP、executor、database pool 等 meters；可导出 Prometheus/OTLP，也有 simple in-memory registry。[Spring Boot Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- Boot 通过 Micrometer Tracing 支持 OpenTelemetry + OTLP，并提供 `spring-boot-starter-opentelemetry`；export 需要相应 endpoint/configuration。[Spring Boot Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)

**架构推论：** Core 必须落地 health/readiness、JSON logs + correlation ID，以及低 cardinality meters：HTTP error/latency、DB pool、accepted/rejected location（reason enum）、matching close duration/outcome、SSE active/connect/disconnect、ETA failures/latency。任何 coordinate、address、raw token、Delivery/Courier/Link ID 都不能成为 log field、exception body、metric tag 或 trace attribute。

OpenTelemetry exporter 只在部署已有 collector/backend 时启用；没有 backend 时保留 Micrometer observation seam 和 correlation logs，不为了一个单体再运营 Collector。Tracing sample rate 和 retention 属于部署配置，而不是把 sensitive payload 交给 auto-instrumentation 的许可。

### Testcontainers 与验收

Testcontainers 官方 PostgreSQL module 启动真实 PostgreSQL container；官方明确说明，与 H2 相比，container 的价值是运行真实 DB、覆盖 H2 未模拟的数据库特性。[Testcontainers PostgreSQL](https://java.testcontainers.org/modules/databases/postgres/)、[Testcontainers Database Containers](https://java.testcontainers.org/modules/databases/)

**架构推论：** 测试分三层：

1. 不启动 Spring 的 domain tests：状态机、ranking、freshness、ETA presentation、privacy projection。
2. Testcontainers PostgreSQL integration tests：运行真实 Flyway，验证 partial indexes、row locking、round-close races、rotation/revocation、audit、provider failure contract。
3. 浏览器 E2E：Dispatcher/Courier/Recipient 跨角色主线，fragment 被清除、cookie/CSRF、SSE reconnect snapshot、permission/visibility simulation、stop/terminal cleanup。

必须有并发 barrier test 同时关闭 overlapping rounds，重复数百次仍保持每 Delivery/Courier 最多一个 Active Assignment；不能用串行 unit test 代替。Location store 用 fake clock 做 30s/2m、out-of-order、generation race。Provider 使用 contract stub/WireMock，不在 CI 调真实付费 API；少量手动 sandbox smoke test 验证 vendor field mapping。

## 10. Expansion substitution：只有触发条件成立才替换

| 候选 | 官方能力/代价事实 | 允许引入的证据 | 本项目边界 |
|---|---|---|---|
| Redis | Key 可设 TTL 自动删除；Pub/Sub 是 at-most-once，subscriber 断线会永久错过；Redis 可完全关闭 RDB/AOF persistence；server-side script/function 可原子执行 conditional update。[EXPIRE](https://redis.io/docs/latest/commands/expire/)、[Redis Pub/Sub](https://redis.io/docs/latest/develop/pubsub/)、[Redis persistence](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)、[Redis scripting](https://redis.io/docs/latest/develop/programmability/eval-intro/) | 必须多 Boot instances，或 2,000 SSE/200 location writes/s gate 证明单实例 memory fan-out/latest store 失败；也可因多实例 tracking-link rate limit 需要共享计数 | Redis Hash/JSON 只存每 Courier 一个 latest snapshot + TTL，关闭 persistence/backup；Lua/Function 按 generation+recordedAt 原子替换；Pub/Sub 只发 invalidation，subscriber 重连先读 latest snapshot。Streams/AOF/RDB 不得暗中形成 Route History |
| PostGIS | `ST_DWithin` 可使用 GiST spatial index；`ST_DistanceSphere` 返回 lon/lat spherical distance metres。[PostGIS `ST_DWithin`](https://postgis.net/documentation/tips/st-dwithin/)、[PostGIS `ST_DistanceSphere`](https://postgis.net/docs/ST_DistanceSphere.html) | `EXPLAIN (ANALYZE, BUFFERS)` 和 matching load 显示 Java/Haversine/zone filter 是 close-selection 的主瓶颈，或 zone geometry 数量/复杂度显著增长 | 它是 PostgreSQL extension，不是第二 truth。优先用于 durable pickup/handoff/zone geometry。不能为使用 PostGIS 而建立 durable LocationPing history；latest coordinates 若进入 DB 必须仍是单行、ephemeral、无 backup 的专门设计 |
| Kafka | Kafka topics 持久保存 events，consumption 后不会删除，并按 retention 保留；partition 为 scale/order 单位。[Apache Kafka Introduction](https://kafka.apache.org/documentation/) | 出现多个独立服务/consumer，需要 durable replay 的非坐标 domain events，且已有 outbox/idempotent-consumer 设计；当前 Expansion spec 没有这一需求 | raw location event 绝不能进 Kafka，因为 retention/replay 与 no Route History 冲突。仅为了 SSE fan-out 或“一条 event 两个本地 handler”不构成证据；Core/既定 Expansion 默认拒绝 |
| WebFlux | fully non-blocking、Reactive Streams backpressure、小固定 event-loop；官方同时指出 blocking JDBC/JPA 常见架构更适合 MVC，迁移有额外复杂度。[Spring WebFlux overview](https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html) | 在调好 MVC async executor、heartbeat、payload 和 DB 后，2,000 SSE gate 仍明确由 blocking response writes/thread-memory 导致失败，并且愿意把相关 I/O 改成 end-to-end non-blocking | 只替换 web adapter，不改变 Postgres truth、HTTP command/SSE contract 或 privacy rules。把 JDBC 包进 reactive type 不等于获得 non-blocking stack |

Redis 与 PostGIS 可能在同一个 Expansion 里分别解决 multi-instance latest state 和 large spatial query，但不是“技术清单套餐”。Kafka 不应承载坐标；若没有 durable cross-service consumer，它在六周内没有 substitution trigger。WebFlux 也不应与 MVC 同时维护两套 controller。

## 11. 交付次序与架构验证点

这不是新的产品范围决定，而是让架构风险尽早暴露的实现次序推论：

1. 先建立 Boot/React 同源骨架、PostgreSQL/Flyway、session/CSRF、CI/Testcontainers。
2. 先实现 Delivery lifecycle + DB constraints，再做 Matching concurrent close；不要先画 UI 后补一致性。
3. 实现 HMAC link derivation、fragment exchange、rotation/revocation 和 security headers；以 automated log scan 验证 raw token 不落地。
4. 实现内存 LatestLocationStore、foreground client 和 freshness/cleanup；此时就跑 location privacy tests。
5. 加 provider ports、MapLibre、geocode confirmation、ETA failure semantics。
6. 最后接 SSE；因为权威 snapshot 已存在，SSE 只需可靠刷新而无需创造第二状态模型。
7. 先通过 Core 15-minute gate，再运行 Expansion 30-minute gate；只有表中 trigger 有测量证据时写 ADR 并 substitution。

最终架构应能用一句话解释：**PostgreSQL 决定发生了什么，内存只保存此刻在哪里，HTTP 改变事实，SSE 通知浏览器重新看事实；任何可选 scale component 都不能把 latest-only 位置悄悄变成历史。**
