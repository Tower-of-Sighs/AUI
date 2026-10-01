# BlueMap chunk preview (NeoForge 26.2)

`BlueMapChunkPreview` uses BlueMap 5.24's `HiresModelManager`, resource-pack parser, `.prbm` tiles and official WebGL viewer. An AUI `<iframe>` displays the map and forwards mouse drags and wheel input. The BlueMap MIT license is bundled in the AUI JAR.

Capture blocks on the level's owning thread. Before a level exists, fill `ChunkMapSnapshot.Builder` from generated block columns. Full block snapshots use `step=1`; a larger `step` scales all three coordinate axes for a coarse LOD. Keep a preview session for the screen lifetime:

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

Reserve a sized `<iframe id="chunk-map">` with no initial `src`. Set its URL once the first frame is ready. The bundled `aui-live.js` replaces subsequent scenes with BlueMap's tile loader and original materials, so updates do not navigate the WebView. Cancel obsolete work after a snapshot change and call `preview.close()` when the screen closes. Snapshots support up to 512×512 columns; tiles are built in parallel and replaced geometry is disposed. BlueMap's resource-extension pack is cached in the supplied directory. The tile server binds only to `127.0.0.1` on an ephemeral port.

BlueMap supplies block and fluid models, colors, tile format, WebGL shaders and camera controls. The snapshot stores block states and surface biome tint; unfinished preview chunks use skylight value 15. Entities, block entities, structures and real block lighting absent from the snapshot cannot be reconstructed by the viewer. Game GUI labels remain unselectable, while text inputs retain editing and selection.

Automatic framing uses the complete tile geometry bounds and iframe aspect ratio, while preserving user zoom. Large areas uniformly convert tile positions, height and camera into presentation units without changing the physical footprint. Pixel updates use bulk row copies and one merged dirty-region upload per draw. A native packet with insufficient room for another pixel row continues in the next packet, keeping every payload within 256 KiB.

Builder `verticalStep` defaults to horizontal `step` and can be set separately for better macro height precision. Embedded pages may post `{type:'aui-camera', rotationDelta:0.2}` or `zoomFactor` to the viewer origin without navigation; `reset`, `yaw` and `angle` reset the camera. The viewer reports `aui-preview-state` to its parent with physical span, model revision and camera data.
