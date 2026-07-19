# resource.html browser vs AUI visual diff - 2026-07-15 10:17:39

Comparison target:

- Browser reference: `D:\work\AUI\img_2.png`
- AUI screenshot: `D:\work\AUI\2026-07-15_10.17.39.png`
- Same AUI screenshot also exists at: `D:\work\AUI\run\screenshots\aui\2026-07-15_10.17.39.png`
- Page: `file:/D:/work/AUI/src/main/resources/assets/apricityui/apricity/devtools/resource.html`
- Current page meta:
  - `<meta name="aui-font-mode" content="web">`
  - `<meta name="aui-viewport" content="mode=browser">`

Important context:

- The browser screenshot is the initial/static page state.
- The AUI screenshot is an interaction-driven state:
  - `LEVEL.DAT` has been clicked and is selected.
  - The detail panel is active and populated.
  - `+ NEW` has been clicked through the test driver.
  - A new `AUTO_PROMPT_FILE` card exists.
- Therefore selected-card styling, the populated detail panel, and the extra file card are state differences first. They must not be treated as static render regressions unless a matching browser interaction screenshot is captured.

## 0. Screenshot and viewport differences

1. Browser image size is `2560x1316`.
2. AUI image size is `2560x1476`.
3. Both screenshots start directly at page content `y=0`; this AUI screenshot does not include a Windows/Minecraft title bar at the top.
4. The AUI screenshot is `160px` taller than the browser screenshot, so lower blank space cannot be compared directly.
5. Browser screenshot has rounded outer corners and a dark outer crop/border at the page container edges.
6. AUI screenshot is rectangular and does not have the same rounded browser capture boundary.
7. Browser bottom edge is the rounded captured browser/page container edge.
8. AUI bottom edge has a dark horizontal game/window boundary line.
9. Browser page content height ends around `y=1316`; AUI page content continues to `y=1476`.
10. Any whole-image pixel diff will over-count the bottom `160px` height mismatch.
11. A proper automated diff should crop both screenshots to the common page viewport area before scoring.

## 1. Overall layout

1. Header height visually matches closely in both images: the purple divider is around `y=100`.
2. Three-column structure exists in both screenshots: sidebar, content, detail panel.
3. Browser left sidebar right border is around `x=495`.
4. AUI left sidebar right border is around `x=488`.
5. AUI sidebar is about `7px` narrower than browser.
6. Browser detail panel left border is around `x=2030`.
7. AUI detail panel left border is around `x=2035`.
8. AUI detail panel starts about `5px` farther right.
9. Because the sidebar is narrower and detail starts farther right, AUI middle content area is slightly wider.
10. Browser content begins around `x=553`.
11. AUI content begins around `x=546`.
12. AUI main content starts about `7px` farther left.
13. Browser content horizontal line ends before the detail panel around `x=1972`.
14. AUI content horizontal line reaches the detail boundary near `x=2035`, because the current selected/active state makes the content header line span to the panel boundary.
15. The AUI page appears vertically more compressed near the main content top: file cards start higher relative to the content header than in the browser.
16. The AUI page has much more visible blank area below the file grid due to the taller screenshot.

## 2. Header structure

1. Header bottom purple line exists in both.
2. Header bottom line color is close to browser purple.
3. Header bottom line thickness is visually close.
4. Browser header has a faint vertical repeating grid pattern.
5. AUI header grid pattern is visible but much fainter.
6. AUI grid lines appear less distinct against white.
7. The browser grid has a softer and more regular 1px vertical cadence.
8. AUI grid appears more dependent on pixel rounding and is harder to see.
9. Header scanline animation cannot be compared from a single static screenshot unless both are captured at the same animation time.
10. AUI top-right buttons are now aligned to the right side, unlike older screenshots where they were in the middle.
11. Browser right button group starts around `x=2070`.
12. AUI right button group starts around `x=2084`.
13. AUI button group is about `14px` farther right than browser.
14. Browser `+ NEW` right edge is around `x=2512`.
15. AUI `+ NEW` right edge is around `x=2517`.
16. AUI right margin is slightly smaller than browser.
17. Browser button height is close to AUI.
18. AUI button borders are sharper and slightly heavier-looking.
19. Browser button borders have smoother antialiasing.
20. Button text content matches: `◀ BACK`, `▲ UP`, `+ NEW`.
21. AUI button text is slightly narrower/denser.
22. Browser button symbols are smoother.
23. AUI symbols have harder edges.
24. Button gaps are close, but AUI appears a little tighter due to text and border rendering.

## 3. Logo and top-left branding

1. Browser logo purple square begins around `x=47`.
2. AUI logo purple square begins around `x=40`.
3. AUI logo block is about `7px` farther left.
4. Browser logo block top begins around `y=22`.
5. AUI logo block top begins around `y=21`.
6. Vertical logo position is close.
7. Browser logo square size appears around `58x58` physical pixels.
8. AUI logo square size is very close, possibly 1-2px different due to animation frame or scale rounding.
9. Browser logo inner white square has softer edges.
10. AUI logo inner white square is more pixel-crisp.
11. Browser `MINE//EXPLORER` begins around `x=125`.
12. AUI `MINE//EXPLORER` begins around `x=118`.
13. AUI title text is about `7px` farther left, consistent with the logo shift.
14. Browser brand text has slightly wider character spacing.
15. AUI brand text is slightly more condensed.
16. Browser brand text antialiasing is smoother.
17. AUI brand text is darker/crisper at edges.
18. AUI brand text height is close but perceived slightly heavier.
19. The gap between logo block and brand text is close, but AUI reads tighter because its text starts earlier and characters are narrower.

## 4. Breadcrumb navigation

1. Browser `ROOT` starts around `x=557`.
2. AUI `ROOT` starts around `x=544`.
3. AUI breadcrumb group is about `13px` farther left.
4. Browser `WORLDS` starts around `x=724`.
5. AUI `WORLDS` starts around `x=704`.
6. Browser current `SURVIVAL` starts around `x=926`.
7. AUI current `SURVIVAL` starts around `x=906`.
8. AUI breadcrumb spacing is slightly tighter overall.
9. Browser separator triangles are smoother and more centered.
10. AUI separator triangles are crisper and may sit slightly higher.
11. Browser inactive breadcrumb text is lighter gray.
12. AUI inactive breadcrumb text appears slightly darker or higher contrast.
13. Current `SURVIVAL` underline exists in both.
14. Browser current underline appears slightly lower and smoother.
15. AUI current underline is close but sharper.
16. Browser breadcrumb text width is slightly wider.
17. AUI breadcrumb text width is slightly narrower.

## 5. Sidebar geometry

1. Browser sidebar width is about `495px`.
2. AUI sidebar width is about `488px`.
3. AUI sidebar right border is about `7px` left of browser.
4. Sidebar background is white in both.
5. Sidebar right border color is close.
6. AUI sidebar border is more pixel-sharp.
7. Browser selected row extends to `x=495`.
8. AUI selected row extends to `x=488`.
9. AUI selected row is naturally shorter because the sidebar is narrower.
10. Browser selected row left purple rail begins a few pixels inside the capture edge.
11. AUI selected row rail begins at the very left edge of the page content.
12. The selected row background color is close, but AUI appears slightly flatter.
13. Browser sidebar content appears a little more spacious horizontally.
14. AUI sidebar content appears slightly tighter.

## 6. Sidebar title

1. Browser `DIRECTORIES` row begins below header at about `y=101`.
2. AUI `DIRECTORIES` row begins at about `y=101`.
3. Vertical start of sidebar title is close.
4. Browser triangle before `DIRECTORIES` sits around `x=53`.
5. AUI triangle sits around `x=43`.
6. AUI triangle is about `10px` farther left.
7. Browser `DIRECTORIES` text begins around `x=81`.
8. AUI `DIRECTORIES` text begins around `x=70`.
9. AUI title text is about `11px` farther left.
10. Browser title letter spacing is visibly wider.
11. AUI title letter spacing is close but still slightly narrower.
12. Browser purple title color appears a little lighter/softer.
13. AUI title color is close but harder-edged.
14. Browser title underline starts around `x=50`.
15. AUI title underline starts around `x=42`.
16. AUI title underline is left-shifted with the title block.
17. Browser title underline length appears around `100px`.
18. AUI title underline appears around `105px`, close but not identical.
19. Browser title separator gray line continues to sidebar boundary.
20. AUI title separator line also continues to sidebar boundary, but the boundary is left-shifted.

## 7. Sidebar tree rows

1. Browser `WORLDS` row text begins around `x=139`.
2. AUI `WORLDS` row text begins around `x=129`.
3. AUI tree text is generally about `8-12px` farther left.
4. Browser tree icons are left of labels and are correctly ordered.
5. AUI tree icons are also left of labels now.
6. The previous severe SVG order issue is not visible in this screenshot.
7. Browser folder icons have smoother fills and edges.
8. AUI folder icons have harder edges.
9. Browser file icon strokes are smoother and slightly thinner.
10. AUI file icon strokes are sharper and slightly heavier.
11. Browser lock icon shackle is smoother.
12. AUI lock icon shackle is more pixel-crisp.
13. Browser row vertical spacing is close to AUI.
14. AUI text baseline appears slightly different, likely font ascent/descent.
15. Browser collapsed triangles have smoother glyph edges.
16. AUI collapsed triangles look sharper and slightly smaller.
17. Browser expanded downward triangles are smoother.
18. AUI downward triangles look more angular.
19. Browser nested indentation appears a little wider.
20. AUI nested indentation appears slightly tighter.
21. Browser `SURVIVAL` selected row text starts around `x=165`.
22. AUI `SURVIVAL` selected row text starts around `x=158`.
23. AUI selected label is about `7px` farther left.
24. Browser `LEVEL.DAT` child text starts around `x=194`.
25. AUI `LEVEL.DAT` child text starts around `x=187`.
26. AUI child item text is about `7px` farther left.
27. Browser final `CONFIG` row sits much higher relative to image bottom because the browser image is shorter.
28. AUI final `CONFIG` row has more blank space below because the image is taller.

## 8. Main content header

1. Browser main title `SURVIVAL` starts around `x=553`.
2. AUI main title starts around `x=546`.
3. AUI main title is about `7px` farther left.
4. Browser title top is around `y=178`.
5. AUI title top is around `y=154`.
6. AUI title is about `24px` higher than browser.
7. Browser title baseline appears lower relative to the header divider.
8. AUI content vertical padding above title is smaller.
9. Browser title text appears slightly larger or more widely tracked.
10. AUI title text is slightly narrower and harder-edged.
11. Browser `5 ITEMS` is visible at the top right of the content header.
12. AUI `5 ITEMS` is not visible in the current screenshot.
13. The missing `5 ITEMS` is likely not because the content count is absent in DOM; it may be obscured or outside the captured visual region due to selected/detail state, content width, or text color/rendering.
14. Browser content header purple line begins around `x=553`.
15. AUI content header purple line begins around `x=546`.
16. AUI line starts about `7px` farther left.
17. Browser line is around `y=250`.
18. AUI line is around `y=235`.
19. AUI line is about `15px` higher.
20. Browser line ends around `x=1972`.
21. AUI line ends at the detail panel border around `x=2035`.
22. AUI line is longer because its content area/detail boundary is farther right or line width resolution differs.
23. Both screenshots now show a purple content line, not the older black-line defect.
24. AUI content line appears slightly sharper.

## 9. File grid placement

1. Browser first row cards begin around `y=295`.
2. AUI first row cards begin around `y=278`.
3. AUI file cards start about `17px` higher.
4. Browser first card left edge is around `x=553`.
5. AUI first card left edge is around `x=546`.
6. AUI first card is about `7px` farther left.
7. Browser first card width appears around `260px`.
8. AUI first card width appears around `263px`.
9. AUI card width is close, possibly a few pixels wider.
10. Browser card height appears around `245px`.
11. AUI card height appears around `237px` for the selected first card.
12. AUI card is slightly shorter.
13. Browser row gap between cards is about `29-31px`.
14. AUI row gap is similar.
15. Browser fifth card right edge is around `1972`.
16. AUI fifth card right edge is around `1978`.
17. AUI first row extends a few pixels farther right.
18. Browser all five cards fit in one row.
19. AUI all original five cards also fit in one row.
20. AUI contains a sixth card on a second row due to the interaction test.
21. Browser does not contain the sixth card.
22. The sixth card is not a framework rendering mismatch; it is caused by the AUI test interaction clicking `+ NEW`.
23. Browser file card borders are light gray and antialiased softly.
24. AUI file card borders are close in color but sharper.
25. Browser unselected cards have white background.
26. AUI unselected cards also have white background.
27. AUI selected `LEVEL.DAT` card has a purple border and pale selected background.
28. Browser reference has no selected card, so that selected styling cannot be compared from these two images.
29. AUI selected card has a purple folded-corner pseudo element at the top-right.
30. Browser reference has no equivalent state visible.

## 10. File card content

1. Browser file icons are centered horizontally in each card.
2. AUI file icons are also centered horizontally.
3. Browser file icon top offset within card is larger.
4. AUI icon content appears slightly higher inside cards.
5. Browser `LEVEL.DAT` icon top is around `y=347`.
6. AUI `LEVEL.DAT` icon top is around `y=337`.
7. AUI icon is about `10px` higher in image coordinates.
8. Browser `LEVEL.DAT` filename starts around `y=445`.
9. AUI `LEVEL.DAT` filename starts around `y=427`.
10. AUI filename is about `18px` higher.
11. Browser `256 KB` starts around `y=486`.
12. AUI `256 KB` starts around `y=466`.
13. AUI meta text is about `20px` higher.
14. AUI card content stack is vertically tighter.
15. Browser filename text is slightly wider.
16. AUI filename text is slightly narrower/harder.
17. Browser file meta gray is softer.
18. AUI file meta gray is close but sharper.
19. Browser folder icons for `REGION` and `PLAYERDATA` are smoother.
20. AUI folder icons are close in shape but have more rigid edges.
21. Browser `ICON.PNG` internal SVG picture shape has softer purple opacity.
22. AUI `ICON.PNG` internal purple area looks more solid/blocky.
23. Browser lock icon is smoother and shackle curvature reads cleaner.
24. AUI lock icon is more jagged/crisp.
25. Browser `SESSION.LOCK` filename width appears slightly wider.
26. AUI `SESSION.LOCK` filename is slightly narrower.
27. AUI sixth card filename wraps as `AUTO_PROMPT_FIL` and `E`.
28. This wrap is expected for the long generated filename in the current card width.
29. No browser comparison exists for the generated card unless the same prompt interaction is performed in the browser.

## 11. Detail panel state and geometry

1. Browser reference detail panel is empty.
2. AUI detail panel is active and populated because `LEVEL.DAT` was clicked.
3. Browser shows centered empty prompt: `SELECT FILE TO VIEW DETAILS`.
4. AUI shows selected-file icon, title, metadata rows, path, and tags.
5. This is primarily an interaction-state mismatch.
6. Browser detail panel left border is around `x=2030`.
7. AUI detail panel left border is around `x=2035`.
8. AUI detail panel starts about `5px` farther right.
9. Browser detail panel width is about `530px`.
10. AUI detail panel width is about `525px`.
11. AUI detail panel is slightly narrower.
12. Browser detail panel background is white.
13. AUI detail panel background is white.
14. Browser detail panel border is light gray and soft.
15. AUI border is close but sharper.
16. AUI active panel has a purple vertical rail on the left edge.
17. Browser empty panel has no active rail.
18. AUI detail icon decoration includes rotated purple outline squares.
19. Browser empty panel has no icon decoration.
20. AUI detail title `LEVEL.DAT` is bold and centered near top.
21. Browser empty prompt is small gray uppercase text around the upper-middle of panel.
22. AUI metadata row labels and values render correctly.
23. AUI tags `NBT` and `WORLD` render as purple pills.
24. Browser reference cannot validate the active detail layout.
25. A separate browser screenshot after clicking `LEVEL.DAT` is required to compare detail active state.

## 12. Typography

1. AUI text is consistently more pixel-crisp than browser text.
2. Browser text antialiasing is smoother/subpixel-like.
3. AUI text appears slightly darker at edges.
4. Browser large title `SURVIVAL` appears slightly wider.
5. AUI large title appears slightly more condensed.
6. Browser brand text `MINE//EXPLORER` appears slightly wider.
7. AUI brand text is slightly narrower.
8. Browser sidebar `DIRECTORIES` letter spacing appears wider.
9. AUI `DIRECTORIES` is close but still tighter.
10. Browser tree item text is a little more open.
11. AUI tree item text is denser.
12. Browser filename text is smoother.
13. AUI filename text is more rigid.
14. Browser gray text such as `5 ITEMS` and `256 KB` is lighter/smoother.
15. AUI gray text is close in color but harsher.
16. AUI generated filename wraps with a visually awkward final single `E`; browser state does not include that generated item.
17. The text differences likely come from font fallback, glyph rasterization, letter spacing, line-height, and scaling/rounding together.
18. The page requests `Chakra Petch` but does not embed it through `@font-face`.
19. Browser and AUI may still be using different installed fallback behavior even with `aui-font-mode=web`.

## 13. SVG and vector rendering

1. SVG icons now appear in the correct structural positions.
2. Browser SVG strokes are smoother.
3. AUI SVG strokes are more aliased/crisp.
4. Browser document icon black outline looks thinner.
5. AUI document icon black outline looks heavier.
6. Browser purple document lines are softer.
7. AUI purple document lines are sharper.
8. Browser folder icon edges have better antialiasing.
9. AUI folder icon edges are blockier.
10. Browser image icon has a more delicate mountain/photo interior.
11. AUI image icon interior has more solid purple blocks.
12. Browser lock icon shackle has smoother curvature.
13. AUI lock icon shackle is more angular.
14. AUI active detail large icon repeats the same SVG stroke/antialiasing differences.
15. AUI rotated decorative squares in detail panel appear correctly, but no browser active-state reference is available in this comparison.

## 14. Color and background layers

1. Overall white and off-white surfaces are close.
2. Browser content background subtly reads as `#fafafa`.
3. AUI content background is close but appears flatter/more uniform.
4. Browser white panels and off-white page background have slightly clearer separation.
5. AUI surface separation is weaker in large blank areas.
6. Purple theme color is close.
7. AUI purple elements are sometimes a little more saturated or sharper due to edge rendering.
8. Browser selected sidebar background is pale purple.
9. AUI selected sidebar background is close.
10. Browser card border gray is soft.
11. AUI card border gray is close but crisper.
12. Header grid alpha in browser is more visible.
13. Header grid alpha in AUI is too faint.
14. Browser gray labels are smoother and slightly lighter.
15. AUI gray labels are harder and may appear darker.

## 15. Pseudo elements and animation-related visuals

1. Header `::before` repeating-grid exists in browser.
2. Header `::before` appears to exist in AUI but is much less visible.
3. Header `::after` scanline cannot be judged reliably from one static frame.
4. Logo block `::after` white square exists in both.
5. Sidebar title `::after` underline exists in both.
6. Breadcrumb current `::after` underline exists in both.
7. Content header purple `::after` line appears in both current screenshots.
8. File selected card pseudo decoration appears in AUI because the file is selected.
9. Browser screenshot has no selected file card, so selected pseudo decoration cannot be compared.
10. Action button hover pseudo background is not visible in either screenshot.
11. Hover pseudo states are not comparable from these images.

## 16. Interaction-state differences

1. Browser has no selected file card.
2. AUI has `LEVEL.DAT` selected.
3. Browser has an empty detail panel prompt.
4. AUI has an active detail panel.
5. Browser file count shows `5 ITEMS`.
6. AUI has six cards after `+ NEW`, but `5 ITEMS` is not visibly updated in this screenshot.
7. AUI contains `AUTO_PROMPT_FILE`.
8. Browser does not contain `AUTO_PROMPT_FILE`.
9. Browser file grid has one row.
10. AUI file grid has a second row due to the generated file.
11. AUI selected state changes the first card background, border, and corner decoration.
12. Browser reference cannot validate whether these selected styles match browser behavior.
13. AUI detail panel active rail and tags are visible.
14. Browser reference cannot validate active detail panel spacing, icon decoration, metadata rows, or tags.

## 17. Things that now look fixed compared with older defects

1. Header action buttons are no longer stuck near the middle; they are right-aligned.
2. Sidebar tree icons are no longer painted after/over text; icon order is correct.
3. Content header underline is purple, not black.
4. The five original file cards fit in a single row.
5. Detail panel exists at the right side with a close width.
6. Pseudo-generated decorations are visibly present in multiple places.
7. SVG icons render rather than disappearing.
8. Prompt/new interaction works in AUI, as shown by `AUTO_PROMPT_FILE`.
9. File selection interaction works in AUI, as shown by selected `LEVEL.DAT` and active detail panel.

## 18. Remaining high-priority mismatches

1. AUI screenshot height does not match browser screenshot height.
2. AUI sidebar is about `7px` narrower.
3. AUI detail panel starts about `5px` farther right.
4. AUI content starts about `7px` farther left.
5. AUI main content is vertically higher by roughly `15-25px` in the title/card region.
6. AUI card stack is vertically tighter.
7. AUI typography is slightly narrower and harsher.
8. AUI SVG/vector antialiasing is harsher.
9. AUI header grid/repeating-gradient is too faint.
10. Browser and AUI are not in the same interaction state.
11. Browser active detail/selected-card state needs its own reference screenshot.
12. AUI `5 ITEMS` is not visible in the current interacted screenshot even though the browser initial screenshot shows it.

## 19. Recommended next comparison captures

1. Capture browser in the same interacted state:
   - click `LEVEL.DAT`;
   - create `AUTO_PROMPT_FILE`;
   - capture after animations settle or with animations disabled.
2. Capture AUI in the initial/static state without `APRICITYUI_TEST_INTERACTION=resource-browser`.
3. Capture both at the same viewport height.
4. Capture one variant with animations disabled for deterministic comparison.
5. Crop out external rounded/capture borders before image diff.
6. Add a small browser/AUI metric harness for:
   - sidebar border x;
   - detail border x;
   - content title y;
   - first card x/y/width/height;
   - header button group x/right edge;
   - text widths for key labels.

## 20. Suspected implementation areas

1. CSS px / viewport scaling:
   - explains sidebar width, detail boundary, and content x offsets.
2. Vertical layout and line-height:
   - explains title/card y offsets and tighter card content.
3. Font fallback and text rasterization:
   - explains narrower text and harder glyph edges.
4. SVG rasterization:
   - explains icon stroke/opacity differences.
5. Gradient alpha and sampling:
   - explains faint header grid.
6. Interaction capture workflow:
   - explains selected/detail/new-file differences and must be normalized before judging static parity.

## 21. Second-pass omissions and corrections

1. Browser screenshot has a real dark outer capture border at the extreme corners and edges:
   - browser `(0,0)` is approximately `rgb(51,51,51)`;
   - browser `(2559,0)` and bottom-right edge are also dark.
2. AUI screenshot does not have the same dark outer border:
   - AUI `(0,0)` is approximately `rgb(252,252,252)`;
   - AUI top-right edge is white.
3. This means top-left/top-right/bottom-right whole-image edge differences are mostly capture-container differences, not page CSS differences.
4. The browser rounded corner is visible because the screenshot includes the browser/page capture boundary. AUI has square page/window edges.
5. Earlier coordinate notes that use `x=0` or `y=0` must be treated carefully because browser edge pixels include the outer capture border while AUI edge pixels are actual rendered page/window pixels.
6. The header bottom purple line is an excellent alignment anchor because both screenshots show `rgb(139,92,246)` around `y=101`.
7. The browser screenshot's dark outer border slightly reduces usable inner content width at the very edges; AUI uses the full rectangular image width.
8. The top-left logo comparison should ignore the browser's outer rounded-corner border and measure from the inner white page area instead.
9. The AUI page background near `(0,0)` is `rgb(252,252,252)`, not pure `#ffffff`; this suggests the framework/window clear color or page background at the top edge is slightly off-white.
10. Browser inner top background is pure white at sampled points such as `(10,10)`, while AUI top-left area samples as off-white. This is a subtle background-layer or capture difference that was under-specified earlier.

## 22. Count text and JS-state discrepancy

1. Browser static screenshot shows `5 ITEMS`.
2. AUI interacted screenshot contains six visible file cards after `AUTO_PROMPT_FILE` is created.
3. Source code in `renderFiles()` sets `contentCount.textContent = \`${node.children.length} ITEMS\`;`.
4. Therefore, after `createNew()` the expected AUI text should be `6 ITEMS`.
5. The AUI screenshot does not visibly show `6 ITEMS` at the content-header right side.
6. This is not fully explained by browser/AUI state mismatch.
7. Possible explanations:
   - `contentCount` exists but is painted behind/under the detail panel boundary or content-header pseudo line;
   - text color/alpha/font rendering makes it too faint in the capture;
   - layout placed it outside the visible content header area;
   - JS updated the grid but did not visibly update `contentCount`;
   - the selected/detail state or animation timing changed the header layout unexpectedly.
8. This should become a separate validation item: after `+ NEW`, query/log `document.getElementById('contentCount').textContent` and its bounding rect in AUI.
9. A matching browser interaction capture should also verify whether the expected browser text is `6 ITEMS`.
10. Until then, do not mark the count mismatch as only an expected interaction-state difference.

## 23. Detail active-state observations missed earlier

1. AUI detail panel active rail is a 2px purple line at the panel left edge and extends vertically through the panel.
2. Browser empty-state panel has no active rail, so the extra purple line is expected from state but still affects visual diff heavily.
3. AUI detail icon decoration includes two rotated outline squares; this comes from `.detail-icon::before` and `.detail-icon::after`.
4. The browser reference empty state has no equivalent decoration.
5. AUI detail icon decoration has thin purple lines that are more aliased than browser would likely render.
6. AUI detail metadata rows use light gray horizontal separators; their color and thickness look close to the page's other separator lines.
7. The detail value column in AUI is right-aligned and appears to use bold text correctly.
8. The path `/worlds/survival/level.dat` is visible in AUI and is horizontally centered/contained enough, but it nearly fills the value area.
9. If the browser active-state screenshot uses the same text, path wrapping and clipping should be checked explicitly.
10. AUI tag pills are rectangular, purple, and correctly spaced, but their text antialiasing remains harsher than browser text.

## 24. Tree/source encoding display caveat

1. PowerShell `Get-Content` displays several source characters as mojibake, for example button/triangle symbols.
2. The screenshots show the symbols rendering as intended in both browser and AUI.
3. Therefore the mojibake is most likely console encoding display, not necessarily file corruption.
4. The visible glyph differences should be attributed to font fallback/rasterization unless byte-level UTF-8 inspection proves otherwise.
5. Do not add an encoding fix solely from PowerShell's displayed text.

## 25. Animation timing caveats added after recheck

1. File cards have `entering` animation on initial render and after `createNew()`.
2. The AUI screenshot was captured after the interaction driver ran, so some card positions, opacity, or transform states may still be influenced by `cardIn` if captured during or soon after animation.
3. The generated `AUTO_PROMPT_FILE` card is especially likely to have been captured during an enter animation window.
4. Browser reference is not in the same animation timeline.
5. Any comparison of vertical card offset should be repeated with animations disabled or after a longer deterministic wait.
6. Logo pulse, breadcrumb separator blink, header scanline, sidebar title line, tree item animation, card animation, and detail animation are all active in this page.
7. Single-frame visual diffs should identify these animated regions separately from stable layout boxes.

## 26. Additional likely root causes to track

1. Header and main y-positions differ even though the header divider aligns well; this points to content padding/layout inside `.content`, not header height.
2. `.content { padding: 32px; }` appears effectively smaller vertically in AUI than browser in the title-to-card region.
3. `.content-header { margin-bottom: 24px; padding-bottom: 16px; }` should be measured directly in both engines.
4. `.file-card { padding: 20px 16px; }` plus text line-height likely explains the AUI card stack being tighter.
5. The generated card wraps after `AUTO_PROMPT_FIL`; because `.file-name` uses `word-break: break-all`, this is legal, but it makes the AUI screenshot visually noisier than the browser reference.
6. The page should have a no-interaction baseline capture and an interaction capture with the same browser state; otherwise static and interactive differences stay mixed.

## 27. Third-pass layout corrections

1. Header vertical alignment is closer than the main content alignment:
   - both screenshots put the header purple divider around `y=100..104`;
   - the main title/card area diverges much more strongly.
2. This means the main vertical drift should not be described as a whole-page y offset.
3. AUI `SURVIVAL` content title appears roughly `20..25px` higher than browser.
4. AUI first card row appears roughly `15..20px` higher than browser.
5. AUI title-to-line and line-to-card spacing look smaller than browser.
6. The likely affected chain is:
   - `.content { padding: 32px; }`;
   - `.content-header { padding-bottom: 16px; margin-bottom: 24px; }`;
   - line-height/font metrics inside `.content-title`;
   - animation transform if captured during `cardIn`.
7. Because header height itself aligns, adjusting viewport scale alone may not fix the content vertical mismatch.
8. A minimal test should measure the actual bounding rects of `.content`, `.content-header`, `.content-title`, `.content-count`, and the first `.file-card` in both browser and AUI.

## 28. Third-pass horizontal spacing details

1. AUI header action buttons are right-aligned but slightly too far right compared with browser.
2. Browser right margin after `+ NEW` is visibly larger than AUI.
3. AUI rightmost button sits closer to the image right edge.
4. Browser action buttons appear a few pixels taller or have more visual breathing room due to antialiasing and border rendering.
5. AUI button glyphs and labels are denser; even where box positions match, the text does not occupy the same visual width.
6. AUI breadcrumb navigation is shifted left and slightly compressed, but that is partly inherited from the narrower logo/brand text.
7. The gap between the brand text and breadcrumb block should be measured from rendered box bounds, not just CSS `margin-left: 40px`, because the brand text width differs.
8. The content card grid gap looks close enough that `gap:16px` is not the primary remaining grid issue.
9. The remaining card x mismatch is more likely from column container width/sidebar-detail boundaries than from grid gap itself.

## 29. Third-pass detail-panel comparison limits

1. The browser reference's empty detail text is positioned around upper-middle of the panel.
2. AUI active detail content starts near the top of the panel and occupies the area where the browser empty prompt would be.
3. This makes direct comparison of detail vertical spacing invalid for this pair of screenshots.
4. Only the detail panel outer geometry should be compared from this pair:
   - left border position;
   - width;
   - background;
   - border color/thickness.
5. Detail inner layout requires a browser screenshot after selecting the same `LEVEL.DAT` file.
6. Conversely, detail empty-state prompt requires an AUI screenshot before selecting any file.
7. The current document should treat browser empty prompt vs AUI selected detail as "state mismatch", not "AUI empty prompt missing".

## 30. Third-pass card-state caveats

1. The first AUI card is selected; the first browser card is not selected.
2. Therefore first-card border color, background tint, left purple rail, and corner triangle should not be used to evaluate normal card rendering.
3. Better normal-card comparisons in this pair are `REGION`, `PLAYERDATA`, `ICON.PNG`, and `SESSION.LOCK`.
4. For those normal cards, AUI still looks vertically tighter than browser.
5. The AUI normal card icons are centered horizontally, so the main card issue is not horizontal centering.
6. The most visible normal-card differences are:
   - card top y is higher;
   - card content stack is tighter;
   - SVG strokes are harder;
   - text is narrower/harsher;
   - card border antialiasing is harder.
7. The selected `LEVEL.DAT` card should be compared only against a browser selected-state screenshot.

## 31. Third-pass background and blank-area notes

1. Browser and AUI lower blank areas are not comparable because screenshot heights differ by `160px`.
2. AUI shows an enormous blank area below the second row because the generated sixth card creates only a partial second row.
3. Browser shows a large blank area below the first row because it has only five cards and a shorter screenshot.
4. The perceived blank-area mismatch is amplified by both height difference and interaction state.
5. AUI bottom-most dark line/color is outside the page content comparison target.
6. Browser bottom dark rounded border is also outside the page content comparison target.
7. Any automated diff should crop to the common inner page rectangle and ideally compare only up to the common viewport height.

## 32. Third-pass updated priority list

1. Capture comparable states first:
   - AUI initial/static without interaction;
   - browser active/selected/new-file state if interaction visuals are needed.
2. Measure actual DOM/render rects for content vertical spacing:
   - `.content`;
   - `.content-header`;
   - `.content-title`;
   - `.content-count`;
   - first normal `.file-card`.
3. Investigate missing/hidden AUI `contentCount` after `createNew()`.
4. Tune content vertical spacing and text line metrics before judging card height.
5. Tune SVG/text antialiasing only after the layout boxes are aligned.
6. Treat header grid faintness as a visual polish issue unless it affects broader gradient rendering.

