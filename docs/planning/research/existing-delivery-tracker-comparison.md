# Delivery Glance 与三个 Delivery Tracker 仓库的对比

研究日期：2026-08-09

> **范围注记（2026-08-09）：** 本文是命名与差异化阶段的历史研究，当时若干决策尚未关闭。现在 [Ticket 12](../12-rescope-to-resume-ready-core.md) 与 [Wayfinder map](../map.md) 已定义精简 Core；下文的“开放决策”和 ETA/完整 Link 生命周期描述不再是当前 Core 承诺。

## 结论

Delivery Glance 不是这三个项目的完整重复，但也不能把“实时位置、配送状态、Spring Boot、React、Redis、Kafka 或 WebSocket”当作原创点：这些能力已经分别出现在现有仓库中。

真正可成立的差异是产品规则的组合：**Recipient-first 的完整体验、Dispatcher 确认的 Courier Recommendation、免注册且可过期的 Tracking Link、Location Freshness 与不确定性表达，以及 Dispatcher → Courier → Recipient 的闭环**。这些目前是已决定的产品方向，而不是已经完成的代码；只有最终实现并测试后，才是可验证的作品差异。

三个仓库中，`wasdfg/DeliveryTracker` 与 Delivery Glance 最接近；`gdntts/delivery_tracker` 是一个位置流后端切片；`HigorRobertoDev/delivery-tracker` 是一个较简单的订单 CRUD/状态全栈应用。

## 比较前提

- Delivery Glance 当前仍只有 [领域模型](../../../CONTEXT.md)、[Wayfinder 地图](../map.md)、研究和原型，尚无应用代码。
- 产品与架构决策现已关闭；[Ticket 12](../12-rescope-to-resume-ready-core.md) 取代本文研究时的开放状态。
- 因此下表中的 Delivery Glance 一栏仍只表示规划，不可表述为已实现功能；与当前范围冲突时以 Ticket 12 为准。

## 总览

| 维度 | Delivery Glance（规划中） | `wasdfg/DeliveryTracker` | `gdntts/delivery_tracker` | `HigorRobertoDev/delivery-tracker` |
|---|---|---|---|---|
| 产品范围 | 面向一个本地 Delivery Team 的 Recipient-first 末端配送追踪产品 | 包含商店、商品、购物车、订单、优惠券、评论、Rider 和配送的完整餐饮配送后端 | 每个订单的 Courier GPS 接收、持久化和实时推送 API | 登录后创建、查看和更新餐饮订单状态的简单全栈应用 |
| 核心角色 | Dispatcher、Courier、Recipient | User、Store Owner、Rider、Admin 等 | 代码只体现位置上报方与按订单订阅的客户端 | 统一的已登录 User；没有独立 Dispatcher/Courier/Recipient 工作流 |
| Courier 分配 | Core 按 On Duty、新鲜位置、无 Active Delivery 过滤，以 Haversine 排出最近三人，由 Dispatcher 直接确认并原子分配 | Rider 从待配送列表自行接单；第一个成功者获得 Delivery | 不负责分配 | 不负责分配 |
| Recipient 访问 | Core 使用免账号、不可预测且可过期的只读 Tracking Link | 使用 JWT/Security 的应用后端；检查范围内未发现等价的限时免登录链接 | 客户端按可知的 order ID 订阅 WebSocket；仓库没有安全模块 | 订单 API 要求 JWT 登录；没有独立只读追踪链接 |
| 实时位置 | Core 只在内存保存最新可用位置，明确 Live/Delayed/Unavailable，不保留 Route History | Rider 位置通过 WebSocket 转发；进入 500 米范围会触发去重后的临近通知 | REST 接收 GPS；PostgreSQL 保存当前位置和完整历史；WebSocket/STOMP 推送 | 没有 GPS、地图或实时推送 |
| ETA / 不确定性 | Core 只承诺状态、下一步和位置新鲜度；外部 travel-time ETA 是 Future Work 13 | 有临近阈值通知；检查范围内未发现完整 ETA/freshness 模型 | 有 `updatedAt`，但产品只输出坐标事件，未见 Recipient-facing freshness/ETA 体验 | 没有 ETA 或位置新鲜度 |
| 生命周期 | Core 为 `AWAITING_COURIER → ASSIGNED → IN_TRANSIT → DELIVERED`，并允许 pickup 前 Cancelled；异常与 Reassignment 后置 | `WAITING → ASSIGNED → PICKED_UP → DELIVERING → DELIVERED/FAILED` | 数据模型含订单状态，但 API 的核心是位置更新 | `RECEBIDO → EM_PREPARO → SAIU_PARA_ENTREGA → ENTREGUE`，另有 `CANCELADO` |
| 前端 | React/Vite 提供最小 Dispatcher、Courier 与移动 Recipient 三个 surface | 此仓库没有 React/TypeScript 前端 | 后端-only；README 只给 JavaScript STOMP 订阅示例 | React 18 + Vite，包含注册、登录、订单创建/列表/状态更新 |
| 技术重叠 | Java 25、Spring Boot 4.1、PostgreSQL/JdbcClient、React/Vite 与 Recipient SSE 单体；Core 明确不用 Redis/Kafka | Java 17、Spring Boot 3.2、JPA、Security/JWT、MySQL、Redis、Kafka、WebSocket、FCM | Java 21、Spring Boot 4.1、JPA、Flyway、PostgreSQL、LISTEN/NOTIFY、WebSocket/STOMP | Java 21、Spring Boot 3.3、Security/JWT、JPA、SQLite、React 18、Vite |
| 已有质量证据 | 尚无应用代码；已定义 Testcontainers 原子竞争、Link/位置隐私、Playwright E2E、CI 和部署验收标准 | 源码树中未见测试、CI 或 Docker 文件 | 只有一个 Spring context smoke test；未见前端、CI 或 Docker | 有后端 controller/service/security 测试；未见前端测试、CI 或 Docker |

## 逐个比较

### `wasdfg/DeliveryTracker`：最接近，但产品边界不同

这个项目不是单纯的位置实验，而是较大的餐饮配送平台后端。源码树包含商店、商品、购物车、订单、优惠券、评论、后台管理、Rider、通知和配送模块；它的 [`build.gradle`](https://github.com/wasdfg/DeliveryTracker/blob/main/build.gradle) 同时引入 Spring Security/JWT、JPA、MySQL、Redis、Kafka、WebSocket、FCM 和 Redisson。

它与 Delivery Glance 的高重叠包括：

- [`DeliveryService`](https://github.com/wasdfg/DeliveryTracker/blob/main/src/main/java/com/example/deliverytracker/delivery/service/DeliveryService.java) 创建 Delivery、让 Rider 领取、推进配送状态并发送通知。
- [`LocationController`](https://github.com/wasdfg/DeliveryTracker/blob/main/src/main/java/com/example/deliverytracker/delivery/controller/LocationController.java) 接收 Rider WebSocket 位置并按订单 topic 转发。
- [`ProximityService`](https://github.com/wasdfg/DeliveryTracker/blob/main/src/main/java/com/example/deliverytracker/delivery/service/ProximityService.java) 计算与送达地址的距离，进入 500 米后通过 Redis 防重并发出“即将到达”通知。
- [`DeliveryStatus`](https://github.com/wasdfg/DeliveryTracker/blob/main/src/main/java/com/example/deliverytracker/delivery/entity/DeliveryStatus.java) 已包含等待、已分配、已取件、配送中、完成和失败。

关键差异：它是 **food-ordering platform-first**，而 Delivery Glance 是 **Recipient tracking-first**；它让 Rider 从待单池自助抢单，而 Delivery Glance 由系统推荐、Dispatcher 人工确认；Delivery Glance 还把限时 Tracking Link、Location Freshness、next-step 和 Recipient 信息层级当作 Core，而不是订单平台的附属功能。ETA 只保留为 Future Work。

### `gdntts/delivery_tracker`：相同的“位置流”硬骨头，但不是完整产品

该仓库的 [README](https://github.com/gdntts/delivery_tracker/blob/master/README.md) 明确把范围限定为实时位置 API：Courier 向 `POST /api/location` 上报坐标，客户端订阅 `/topic/order.{id}`。[`LocationService`](https://github.com/gdntts/delivery_tracker/blob/master/src/main/java/dev/gustavodntts/deliverytracker/service/LocationService.java) 在一个事务中更新当前位置、追加位置历史，并通过 WebSocket 发布事件；PostgreSQL trigger 还会通过 `LISTEN/NOTIFY` 驱动另一路推送。其 [`pom.xml`](https://github.com/gdntts/delivery_tracker/blob/master/pom.xml) 使用 Java 21、Spring Boot 4.1、JPA、Flyway、PostgreSQL 和 WebSocket。

这与 Delivery Glance 的位置摄取和实时推送有技术重叠，所以这些不能单独当作项目差异。重要设计差别是 Delivery Glance 有意只保存内存中的最新可用位置、不保留位置历史，并把位置放进完整三角色产品闭环；该仓库则更接近一个可嵌入其他系统的基础设施切片。

一个值得注意的设计差异是：它把 PostgreSQL `LISTEN/NOTIFY` 用作数据库变更广播，而 Delivery Glance Core 已选择 Spring MVC SSE 只发送 Recipient 刷新提示。Redis、Kafka 与 PostGIS 都没有 Core 职责；只有未来测量或真实 durable consumer 才能把它们拉回 backlog。

### `HigorRobertoDev/delivery-tracker`：栈最像，业务最浅

该仓库的 [README](https://github.com/HigorRobertoDev/delivery-tracker/blob/main/README.md) 展示的是登录后的订单管理：注册、登录、创建订单、列出订单、更新订单状态。后端 [`pom.xml`](https://github.com/HigorRobertoDev/delivery-tracker/blob/main/backend/pom.xml) 使用 Java 21、Spring Boot 3.3、Security/JWT、JPA 和 SQLite；前端 [`package.json`](https://github.com/HigorRobertoDev/delivery-tracker/blob/main/frontend/package.json) 使用 React 18 和 Vite。

它与 Delivery Glance 的相似主要是全栈骨架和配送状态领域，而不是核心体验。源码只有 Auth 和 Order 两组主要控制器，以及登录、注册、订单列表和创建表单；没有 Courier 实体、派单、GPS、地图、实时推送、ETA、Location Freshness 或 Recipient Tracking Link。Delivery Glance 若完成已决定的闭环，会在业务模型和实时体验上明显超出它。

## Delivery Glance 真正的差异化

以下组合在检查的三个仓库中没有完整出现：

1. **Recipient-first，而不是订单平台或位置 API first。** Core 首先回答“到哪一步、在哪里、下一步是什么”；“何时到”明确留给 ETA Future Work。
2. **Explainable human-in-the-loop dispatch。** 系统按明确 eligibility/distance 规则推荐 Courier，但必须由 Dispatcher 确认；既不是纯手工，也不是 Rider 抢单。
3. **免注册但有隐私边界。** Recipient 通过不可预测、可过期的 Tracking Link 访问一项 Delivery，而不是注册成平台用户或仅凭可猜的订单 ID 订阅。
4. **可信的位置语义。** 位置必须带 Location Freshness；过期数据明确降级，不能继续标成 live。
5. **完整三方闭环。** Dispatcher 创建和分配，Courier 确认并上报进度，Recipient 在同一 Delivery 上获得状态、位置新鲜度和下一步。
6. **有意限制产品范围。** 不做购物、支付、商店目录、原生 App、路线规划和多租户 SaaS，避免变成另一个外卖平台。

## 不能声称是差异的内容

- Spring Boot + React/Vite。
- 配送状态机。
- Courier GPS 上报与地图移动点。
- WebSocket/SSE 实时推送。
- Redis、Kafka 或 PostgreSQL。
- 保存当前位置和位置历史。
- “Courier 即将到达”的距离阈值通知。

这些都已有直接先例。它们可以是实现手段和面试讨论材料，但不是产品原创性本身。

## 要让差异最终成立，必须落实什么

1. 优先完成 Courier Recommendation + Dispatcher confirmation，不能退化为手工下拉框或 Rider 抢单。
2. 把 Tracking Link 的生成、过期、fragment 兑换和完成后可见范围做成真实安全边界；撤销/恢复属于 Future Work 14。
3. 在 UI 和 API 中实现 Location Freshness 与 stale 状态，而不仅仅保存 `updatedAt`。
4. 让 Recipient 页面同时呈现状态、位置新鲜度和 next step，而不只是地图圆点；不把未实现 ETA 写进 Core 叙事。
5. README 应用一句话清楚定位：**“Recipient-first last-mile tracking with dispatcher-confirmed courier recommendations and trust-aware location freshness.”**
6. 清楚解释为什么 PostgreSQL、latest-only 内存位置和 SSE 足够，以及 Redis/Kafka 为什么暂缓；技术数量不是差异。

## 当前实现边界

- 产品范围以 [Ticket 12](../12-rescope-to-resume-ready-core.md) 为准。
- 技术栈与模块边界以 [Core technical baseline](../implementation/TECHNICAL-BASELINE.md) 为准。
- 可交给 Agent 的工作从 [DG-020](https://github.com/Jamiedz999/delivery-glance/issues/1) 开始。
- ETA、Link recovery、异常/Reassignment、Service Zone、完整 Matching、scale experiment 与 durable event backbone 保留为 Future Work 13–19，不能描述成已实现功能。
