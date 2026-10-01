# BlueMap 区块预览（NeoForge 26.2）

AUI 的 `BlueMapChunkPreview` 使用 BlueMap 5.24 的 `HiresModelManager`、资源包解析、`.prbm` 瓦片和官方 WebGL 页面。地图显示在 AUI 的 `<iframe>` 中，鼠标拖动与滚轮由 AUI 转发给 BlueMap。BlueMap 的 MIT 许可证随 AUI JAR 打包。

在关卡所属线程捕获方块；尚未创建关卡时也可以用 `ChunkMapSnapshot.Builder` 写入实际生成的方块柱。完整方块快照使用 `step=1`；宏观 LOD 可使用更大的 `step`，快照的 X、Y、Z 坐标都按该比例缩放。模组把自己的资源根、Minecraft 客户端 JAR 和缓存目录传给会话，并在屏幕生命周期内持有：

```java
BlueMapChunkPreview preview = new BlueMapChunkPreview(
        List.of(modResourceRoot, minecraftClientJar), cacheDirectory);
ChunkMapSnapshot snapshot = ChunkMapSnapshot.capture(level, min, 96, 96, 160);
AtomicBoolean cancelled = new AtomicBoolean();
preview.render(snapshot, cancelled::get).thenAccept(url ->
        Minecraft.getInstance().execute(() -> {
            if (!frame.hasAttribute("src")) frame.setAttribute("src", url);
        }));
```

页面保留有尺寸的 `<iframe id="chunk-map">`，初始无需 `src`；首帧就绪后填入 URL。后续快照由内置 `aui-live.js` 使用 BlueMap 的瓦片加载器和原材质替换场景，无需重新设置 `src`。切换快照时取消过期任务，屏幕关闭时调用 `preview.close()`。支持最多 512×512 列；瓦片并行构建，旧几何体在替换后释放。BlueMap 资源扩展包缓存在传入的目录，瓦片服务器只绑定 `127.0.0.1` 临时端口。

方块模型、液体、颜色、瓦片格式、WebGL 着色器与相机交互来自 BlueMap。快照记录方块状态和地表群系色，尚未完成的预览区块使用日光值 15；未写入快照的实体、方块实体、结构与真实方块光照无法从空白重建。游戏 GUI 的普通标签不能被选中，输入控件仍可编辑。

相机按所有瓦片的完整模型边界和 iframe 宽高比自动取景；用户缩放后保留其视角。大范围按等比显示单位转换，瓦片、垂直高度与相机一起转换，物理覆盖范围保持不变。增量像素按行批量复制，每个绘制帧只上传合并后的脏区域。原生分包的剩余空间不足一行时在下一包继续，所有 payload 均不超过 256KiB。

Builder 的 `verticalStep` 默认沿用水平 `step`，可单独指定以提高宏观高度精度。嵌入页可用 `postMessage({type:'aui-camera', rotationDelta:0.2}, viewerOrigin)` 或 `zoomFactor` 调整相机，无需重载；`reset` 与 `yaw`/`angle` 用于重置。查看器向父页报告 `aui-preview-state`，包含物理范围、模型版本和相机数据。
