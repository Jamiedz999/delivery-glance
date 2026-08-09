# Tracking Link 安全研究

研究日期：2026-08-09

> **当前实现范围（2026-08-09）：** 本文仍是安全证据库。精简 Core 只实现 fragment bootstrap、不可预测/不落明文的 capability、同源受限会话、通用失效响应、安全响应头、Copy 与自动 Expiry；Rotation、Revocation、Reissue、完整历史和高级告警进入 [Future Work 14](../issues/14-add-tracking-link-recovery.md)。以 [Ticket 12](../issues/12-rescope-to-resume-ready-core.md) 和 [Technical baseline](../implementation/TECHNICAL-BASELINE.md) 为当前实现边界。

## 完整产品安全结论（当前 Core 只采用上方子集）

Delivery Glance 的 Tracking Link 应被明确视为一个**受限、可撤销的 bearer capability**：任何拿到完整凭据的人都能像原 Recipient 一样查看内容；免账号设计本身不能证明访问者身份，也不能阻止转发、截图或已被控制的收件设备。RFC 对 bearer token 的定义正是“持有者即可使用”；W3C TAG 的 capability URL 设计说明也把它定义为“持有 URL 即获得能力”。因此首要目标是减少凭据泄漏的机会和泄漏后的有效时间。[RFC 6750 §1.2](https://www.rfc-editor.org/rfc/rfc6750.html#section-1.2)、[W3C TAG：Capability URLs（Working Draft）](https://www.w3.org/TR/capability-urls/)

建议锁定的安全底线是：

- capability 只允许读取一张 Delivery 的 Recipient-facing tracking view，绝不能据此修改 Delivery、地址或 Assignment。
- 使用 CSPRNG 生成无业务含义的 opaque token，**至少 128 bits 随机性**；对可点击链接没有人工输入成本，Delivery Glance 可把 256 bits 作为项目默认值。OWASP 对自建 session identifier 的当前建议是至少 128 bits 且唯一。[OWASP Session Management：Session ID Length](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#session-id-length)
- token 有服务器端绝对有效期，并可被立即撤销和轮换；轮换后旧 token 以及由它建立的浏览会话都失效。
- 链接第一次被 GET/预览/扫描时**不能**消耗 token、启动 TTL 或改变业务状态；GET 按 HTTP 语义应是 safe/read-only，规范特别指出爬虫和预取会自动访问 GET URL。[RFC 9110 §9.2.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.1)
- Tracking 页面及其 API 不记录原始 token，不进入通用 analytics/session replay，不允许个性化 link preview，不被缓存，并且不发送 referrer。
- 无效、过期和撤销的链接对外显示相同的无敏感信息页面；具体原因只留在 Dispatcher 审计中。

## 威胁模型与能力边界

这条链接保护的是完整 Handoff Address、Courier Display Name、Delivery 状态、ETA，以及 `IN_TRANSIT` 时接近实时的 Courier 位置。主要风险包括：

1. URL 被聊天或邮件预览、安全扫描器、转发服务处理；Microsoft 365 的 Safe Links 会扫描并可能重写邮件中的 URL，Slack 的官方 unfurl 文档也说明其服务会抓取消息 URL。这说明“真正的人点击前 URL 已被自动系统处理”是正常环境，而不是边缘情况。[Microsoft Safe Links 官方说明](https://learn.microsoft.com/en-us/defender-office-365/safe-links-about)、[Slack Link Unfurling](https://docs.slack.dev/messaging/unfurling-links-in-messages/)
2. URL 进入浏览器历史、书签、截图、剪贴板、Referer、反向代理/CDN/APM/错误报告及 Web 服务器日志。RFC 6750 明确不建议把 bearer token 放在页面 URL，因为浏览器历史和服务器日志等位置通常不能充分保护 URL；RFC 9110 也说明 URI 会被显示和记录，不适合承载敏感信息。[RFC 6750 §5.3](https://www.rfc-editor.org/rfc/rfc6750.html#section-5.3)、[RFC 9110 §17.9](https://www.rfc-editor.org/rfc/rfc9110.html#section-17.9)
3. 第三方 analytics、tag manager、错误回放或地图脚本以页面 JavaScript 权限读取 DOM/URL；OWASP 指出第三方 JavaScript 可获得与页面代码相同的权限，并存在敏感信息外泄风险。[OWASP Third-Party JavaScript Management](https://cheatsheetseries.owasp.org/cheatsheets/Third_Party_Javascript_Management_Cheat_Sheet.html)
4. 随机猜测、格式探测、Delivery reference/顺序 ID 枚举，以及针对短 PIN 的在线暴力尝试。
5. Dispatcher 发错人、Recipient 转发、消息账号或终端被控制。bearer-only 方案无法在技术上区分这些人与原 Recipient；能做的是撤销、轮换、缩短暴露窗口和减少页面数据。

不应声称 Tracking Link “确认了 Recipient 身份”。它只证明请求方持有 capability。

## Token 生成、存储与比较

### 必须成为产品/安全要求

- token 必须完整随机、不可预测、唯一、无 Delivery ID、状态、时间或 Recipient 信息等可解码内容。OWASP 同样要求 session ID 无业务含义，业务关联保存在服务器端。[OWASP Session Management：Session ID Content](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#session-id-content-or-value)
- 普通 UUID、顺序 ID 或 Delivery Reference 不能被当作 capability。新版 UUID 标准明确警告：不能假设 UUID 难以猜测，也不能直接把 UUID 用作“持有即授权”的 security capability。[RFC 9562 §8](https://www.rfc-editor.org/rfc/rfc9562.html#section-8)
- 一条 token 只绑定 `tracking:read` 和一张 Delivery。Delivery reference、数据库主键或短码不能单独授权。
- token 明文只在签发并组成分享链接时短暂存在。数据库保存不可逆 verifier（对高熵 token，SHA-256 digest 已能保留其原始猜测强度；也可使用带服务器密钥的 HMAC verifier）。这是基于 OWASP “token 应安全存储、访问 token 不应写日志”的防御性推论。[OWASP Forgot Password：General Security Practices](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html#general-security-practices)、[OWASP Logging：Data to Exclude](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html#data-to-exclude)
- 应使用平台提供的 constant-time primitive 比较固定长度 verifier，避免早停字符串比较造成 timing side channel；OWASP 在其他 secret-token 校验场景中也明确要求 constant-time comparison。若后续采用 Java，可直接评估标准库 `MessageDigest.isEqual`，其文档说明时间不依赖 digest 内容。[OWASP CSRF Prevention：Signed Double-Submit Cookie](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#signed-double-submit-cookie-recommended)、[Java `MessageDigest.isEqual`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html#isEqual(byte%5B%5D,byte%5B%5D))

### 留给架构票

- 128 还是 256 bits、Base64url 编码细节、SHA-256 digest 还是 HMAC verifier、索引方式和 constant-time API。
- opaque server-side token 与 self-contained JWT 的选择。这里更倾向 opaque token：本项目要求即时撤销/轮换，而且没有跨服务离线验证需求；OWASP 也提醒 JWT 会额外引入实现风险。[OWASP Forgot Password：General Security Practices](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html#general-security-practices)

## URL、历史、日志、analytics 与 link preview

可分享链接必然需要一次“bootstrap credential”，但安全目标应是：**凭据只用于建立受限浏览会话，随后立即从地址栏和后续请求中消失。**

### 最抗泄漏的实现方向（架构票决定）

1. 分享形态可采用 `https://track.example/#<token>`。
2. RFC 3986 规定 fragment 在 dereference 前由 user agent 从 URI 分离，不参与发送给 HTTP 服务器，因此普通源站、代理和 preview HTTP fetch 不会收到 fragment。[RFC 3986 §3.5](https://www.rfc-editor.org/rfc/rfc3986.html#section-3.5)
3. 第一方 bootstrap JavaScript 在加载任何其他应用代码或资源前读取 fragment，通过同源 POST 兑换成短期、`Secure`、`HttpOnly`、合适 `SameSite` 的受限 cookie，然后调用 `history.replaceState` 把当前历史记录替换为无 secret URL。`replaceState` 确实会替换当前 history entry 的 URL。[MDN：History.replaceState](https://developer.mozilla.org/en-US/docs/Web/API/History/replaceState)
4. 后续 HTML/API/SSE 请求只使用 cookie/session，不再携带原始 link token；不要把 token 放入 `localStorage` 或 `sessionStorage`，因为同源 JavaScript 都可读取它们。[OWASP Session Management：HTML5 Web Storage](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#html5-web-storage-api)

这不是完美保密：消息/聊天供应商在解析原始消息时仍可能看到完整 fragment，页面第一方 JavaScript 在清除前也能看到它。它主要消除源站 request-target、普通 referrer、HTTP preview fetch 和后续浏览历史中的长期暴露。因此必须配合无第三方脚本和严格日志规则。

若 Core 选择 path 或 query token，二者都会进入 HTTP request-target；不能假设“放 path 比 query 安全”。必须在 CDN、反向代理、Web server、APM、tracing、异常报告和 CSP report **写入前**统一清除/掩码，并在首次验证后重定向或替换为无 token URL。OWASP 明确要求 session identifier 与 access token 通常不得原样写日志，可在确有审计需求时记录散列标识。[OWASP Logging：Data to Exclude](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html#data-to-exclude)

### 页面规则

- Tracking 页面不能安装第三方 analytics、广告、tag manager、session replay 或从 URL 自动采集参数的错误监控。若需要产品指标，只发服务器端聚合事件，不包含 token、地址、精确位置或完整 URL。
- Open Graph/preview metadata 必须固定为通用文案，例如 “Delivery Glance tracking link”，不能包含 Delivery reference、地址、Courier、状态或 ETA。预览方是否遵守 header 不是安全边界。
- 搜索防护可以加入 `X-Robots-Tag: noindex, nofollow, nosnippet`，但它只对愿意遵守的 crawler 有效；Google 也明确说明这类规则只有 crawler 能访问并选择遵守时才有效，不能替代 authorization。[Google Search Central：Robots Meta/X-Robots-Tag](https://developers.google.com/search/docs/crawling-indexing/robots-meta-tag)
- 若页面必须加载第三方地图资源，CSP 只允许明确列出的 provider，token 绝不作为地图请求参数；地图供应商仍会看到 Recipient IP，这一隐私取舍属于架构票。

## 响应头与传输

所有 bootstrap、Tracking HTML、Recipient API、失效页及实时响应都应只经 HTTPS 提供，并设置：

```http
Cache-Control: no-store
Referrer-Policy: no-referrer
X-Robots-Tag: noindex, nofollow, nosnippet
```

- `no-store` 要求 private/shared cache 都不存储请求或响应；`no-cache` 只要求复用前验证，并不阻止存储。RFC 同时提醒 `no-store` 不是单独即可保证隐私的机制，因此还必须保护 URL、日志与传输。[RFC 9111 §5.2.2.5](https://www.rfc-editor.org/rfc/rfc9111.html#section-5.2.2.5)
- `no-referrer` 会完全省略 Referer，比浏览器默认的 `strict-origin-when-cross-origin` 更适合含位置数据的 capability 页面；默认策略在 same-origin 请求仍可能发送完整 path/query。[MDN：Referrer policy configuration](https://developer.mozilla.org/en-US/docs/Web/Security/Practical_implementation_guides/Referrer_policy)
- 还应使用严格 CSP、`frame-ancestors 'none'`、HSTS 和只允许必要源的 `connect-src`/`img-src`；具体 policy 和地图例外留给架构票。[OWASP Content Security Policy](https://cheatsheetseries.owasp.org/cheatsheets/Content_Security_Policy_Cheat_Sheet.html)

## Expiry、revocation 与 rotation

### 可以直接确定的规则

- 有效期由服务器保存并按服务器时间强制执行，至少包括 `issuedAt/notBefore/expiresAt/revokedAt/generation`；不能依靠浏览器倒计时或 JWT 客户端字段。
- **首次打开不是 activation。** 若产品需要 “active” 概念，应定义为 Dispatcher 明确分享/启用的服务器端事件；这样自动预览不会改变生命周期。
- Tracking Link 在有效期内应可重复打开，因为 Recipient 需要持续追踪；它不是密码重置那种 single-use action token。
- Dispatcher 可随时因发错人、疑似泄漏或 Recipient 请求而撤销。rotation 生成全新 token，旧 generation 立即失效；因安全事件 rotation 时，旧 token 派生出的全部 cookie/session 和实时连接也必须失效。
- 过期 token 不能“恢复”。若仍需访问，Dispatcher 只能签发新 generation，并形成审计事件。
- 记录 creation、enable/share、valid redemption、expiry、revocation、rotation 和 revoked/expired reuse attempt，但日志只使用内部 Link ID 或不可逆短 fingerprint，不保存原 token。OWASP secrets guidance同样要求 secret 可撤销、可轮换且绝不记录明文。[OWASP Secrets Management：Detection lifecycle](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html#83-detection-lifecycle)

### 必须由产品票决定的 policy

权威安全资料不会给出“配送链接应保留几小时”这一业务数字。应选择满足 Recipient 合法查看需求的最短窗口，并明确：

- 是在 Delivery 创建时还是 Dispatcher 第一次分享时签发；从减少未使用 secret 的角度，首次分享时签发更小。
- 绝对 hard cap 多久，以及是否以 scheduled handoff 为基准。
- `DELIVERED`、`CANCELLED`、`UNDELIVERABLE` 后终态页保留多久。终态保留期内显示已确定的最小终态信息；到期后则不再显示任何 Delivery 数据。
- Delivery cancellation 是否只进入通用终态保留，还是在特定隐私事件下立即 revoke。业务终态和安全撤销不应被混成同一概念。
- 是否允许一张 Delivery 同时存在多个有效 recipient links。最容易解释的 Core 是一个 current generation；再次分享可复用 current link，明确 rotation 才废弃旧 link。

## Brute force、rate limiting 与 enumeration

- 高熵随机 token 是首要控制；rate limit 是纵深防御，不能拿短 token 配合限流来替代 entropy。
- 在 token lookup/存在性判断之前，对 tracking bootstrap 施加 source、网络和全局速率控制；对已建立会话另设合理刷新/连接限制。识别分布式高失败率并告警。
- 对格式正确但无效、过期和撤销的 token 返回相同 body、相近处理路径和相同缓存/安全头；不能透露 Delivery 是否存在、曾存在、何时结束或为何撤销。OWASP 对相似 token recovery flow 建议统一消息与响应时间以避免枚举；RFC 7662 也明确要求 inactive 响应不能让调用者区分 token 是未知、过期还是撤销。[OWASP Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)、[RFC 7662 §2.2](https://www.rfc-editor.org/rfc/rfc7662.html#section-2.2)
- 限流时可以使用 `429 Too Many Requests` 和 `Retry-After`；阈值、key 组合、边缘/WAF 配置属于架构与容量测试。RFC 6585 定义了该语义，但不规定如何识别或计数请求者。[RFC 6585 §4](https://www.rfc-editor.org/rfc/rfc6585.html#section-4)
- 不能因为某来源大量猜测就自动 revoke 某个有效 Delivery link，否则攻击者可制造拒绝服务。失败尝试只应触发节流、challenge、告警或人工调查。

## 额外 PIN 是否有实际价值

**有，但价值有明确边界。** PIN 不出现在 URL、单独输入并单独校验时，可以阻止“只从 server log、Referer、浏览器历史或普通 preview fetch 泄漏了 link token”的攻击者。这对本项目最现实的偶发泄漏路径确实有帮助。

但它不是万能的：

- link 与 PIN 若在同一封邮件/同一条消息中发送，消息账号、消息供应商、收件设备或转发内容被完整获取时，两者会一起泄漏；不能把这种组合宣传为真正 MFA。OWASP 强调 factors 必须独立，同类或可由同一攻击同时攻破的凭据仅增加很少保证。[OWASP Multifactor Authentication：Introduction](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html#introduction)
- 短 PIN 的在线猜测空间远小于 128/256-bit token，必须有 per-link 与 per-source attempt limit、递增延迟、审计及安全恢复；不能放 URL、不能写日志、不能长期明文存储。OWASP 的相近 recovery flow 使用 6–12 位、通过 side channel 传递的 PIN；其 OTP 指南还要求短 TTL、single use、严格 attempt limit 和不记录明文。Tracking PIN 虽不是密码重置 OTP，这些 secret-handling 原则仍适用。[OWASP Forgot Password：PINs](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html#pins)、[OWASP MFA：One-Time Password Handling and Storage](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html#one-time-password-otp-handling-and-storage)
- PIN lockout 也能被用来拒绝 Recipient 访问，因此达到阈值后的恢复应由 Dispatcher rotate/reissue，而不是暴露 Delivery 信息或无限锁死。

对 Delivery Glance 的产品建议是：**Core 默认使用高熵 bearer link；把单独 PIN 作为高敏 Delivery 或 Delivery Team policy 的可选 step-up，而不是所有 Recipient 的默认摩擦。** 如果采用 PIN，最好通过不同渠道传递，或让 Dispatcher 将其口头/线下交给 Recipient；具体 PIN 长度、TTL、attempt budget、verifier 与恢复流程属于后续架构/安全验收。

## 无效、过期或撤销链接的安全行为

Recipient-facing 页面统一显示类似：

> This tracking link is no longer available. Contact the delivery team if you need help.

并遵守以下规则：

- 不显示 Delivery reference、Handoff Address、Courier、状态、终态、到期时间或撤销原因。
- 提供固定 Delivery Team 电话/邮箱；不得在页面中自动生成带 Delivery 数据的 support URL。
- 立即清除该 link 派生的 cookie/session，停止 SSE/WebSocket/轮询，移除当前页面中的 tracking data。已打开的页面必须在下一次授权检查或服务端断连时收回内容，不能只在“下次重新打开”才生效。
- invalid、expired、revoked 对外使用同一页面。API 可统一使用 404，HTML 也可统一返回通用页面；最终 status-code/SPA routing 选择属于架构票。不要用 `410 Gone` 只标记“曾经有效”的 token，因为这会形成 existence oracle。
- 若 link 仍处于产品决定的 terminal grace period，它仍是有效 link，按 Recipient Tracking Promise 显示最小终态信息；grace 到期后才进入上述统一失效页。

## 应移交给 “Choose the Core technical architecture” 的事项

以下不是 Tracking Link lifecycle 的产品决定，不应在当前票中假装已经选定：

1. fragment bootstrap + POST exchange，还是 path/query bootstrap + edge redaction。
2. cookie/session 结构、`Secure`/`HttpOnly`/`SameSite`/Path/Domain、CSRF 与多设备策略。
3. token 字节数、编码、digest/HMAC verifier、数据库索引、constant-time API 和 key management。
4. revocation 如何让 API cache、SSE/WebSocket 与已打开页面立即失效。
5. CDN、load balancer、Web server、APM、trace、CSP report、exception telemetry 的 URL redaction 验证。
6. rate-limit 的阈值、维度、分布式计数、429/challenge 策略及 DoS 测试。
7. CSP/HSTS/Permissions-Policy、独立 tracking origin，以及地图供应商与 Recipient IP/位置隐私。
8. PIN 的 verifier、attempt budget、冷却/恢复及不同渠道分发实现。

这些实现最终都应有自动化安全验收：原始 token 不出现在任何日志/telemetry，旧 generation 与派生 session 可立即失效，非法状态响应不可区分，自动 GET 不改变 lifecycle，且所有敏感响应带正确 cache/referrer headers。
