# 可编辑UI设计源与本地导出

Figma 文件：[活动票务与报名系统｜莫兰迪 UI 设计](https://www.figma.com/design/Wb474yxRNd4hICDmyWDkPb)。包含28个独立模块画板、共享配色/间距变量、中文文字样式、Simple Design System 按钮和表单实例，以及30条初始原型导航连接。04“确认报名”导航05，05“确认模拟支付”导航06。

本地总览：`../UI界面/界面索引.html`；可直接双击打开。卡片可进入本地设计预览，主流程可点击跳转。截图见 `../UI界面/`；设计注记同目录。

## 导出方式与限制

Figma画板已创建。批量导出时触发 Starter 套餐 MCP 调用限额，后续不能继续导出或同步局部修正。最终28张模块PNG统一采用下述本地渲染，保证一致性；不能声称它们全部由Figma导出。Figma中的购票页曾成功导出并视觉复核；其他模块已检查本地结果。

`figma-render.js` 为共享界面绘制源，`screens.json` 为28页模块清单。`local-design-runtime.js` 将同一绘制源解释为DOM、Flexbox和SVG；它不访问Figma，不调用远程接口。`index.html` 是可离线浏览的生成物。底部导航高亮、长页侧栏和本地跳转经过局部修正，限额恢复前不再宣称Figma与本地每个细节完全同步。

## 在本机生成和验证

```powershell
node .\phase2\UI设计源\build-local-ui.cjs
node .\phase2\UI设计源\export-local-ui.cjs
node .\phase2\UI设计源\build-gallery.cjs
node .\phase2\UI设计源\verify-ui.cjs

# 浏览器预览；终端会输出随机可用的本地端口
node .\phase2\UI设计源\serve-ui.cjs
```

导出器使用 Playwright 和本机 Edge，默认读取本次工具环境已有依赖路径；换机器时可设置 `UI_NODE_MODULES` 为含 `playwright` 的 node_modules 绝对路径，`UI_BROWSER` 为 Chromium/Edge 可执行文件路径。生成PNG不需要联网。

`export-local-ui.cjs 04 05` 可只重建所选页面。`ui-verification.json` 记录28张PNG签名/尺寸及两个主流程点击检查；`local-render-check.json` 为最近一轮所选页面的越界检查，不代表真实业务系统测试。侧边导航、状态切换与表单值目前是设计演示，不是完整业务交互。

## Figma创建记录

- `figma-foundations.js`：原始配色、语义变量及中文文字样式创建脚本。
- `figma-render.js`：界面结构、正文、海报矢量及通用组件复用。
- `figma-state.json`：文件/会话、变量及样式ID账本；`figma-build-*.json`：各批画板与创建节点ID。
- `figma-frames.json`：初次创建的28张画板ID和尺寸，个别画板高度在购票页修正后调整，不能替代最新本地导出尺寸。
- `download-png.ps1`：仅供已获取且未过期的导出链接下载；不绕过Figma权限或套餐限制。

Figma重复创建脚本有同名画板保护。继续编辑应以现存ID定位并定点修改；不要直接整批重跑生成重复画板。首次使用的 imagegen 图片仅作为风格参考，没有将整张截图导入Figma充当可编辑UI。

## 本次辅助过程事实

需求来自实验一最终 requirements.md、实验二 phase2_req.md 以及本次对本人报名、手机号展示、支付跳转的用户确认。Figma技能用于发现基础组件、建立设计变量、创建28张文字/布局可编辑画板及导航；本地浏览器用于导出和检查。生成中修正水平布局高度、标签截断、侧栏高亮和长页侧栏。限额是工具套餐限制，不是用户授权拒绝。

未编写实验报告正文，也未替用户填写个人判断、优缺点分析或反思；本目录仅保留实际生成过程和可复核产物。
