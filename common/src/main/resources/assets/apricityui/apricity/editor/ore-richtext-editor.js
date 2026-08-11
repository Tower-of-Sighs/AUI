(function () {
    "use strict";

    let STORAGE_KEY = "apricityui.ore-richtext-editor.document.v1";
    let HISTORY_LIMIT = 120;
    let TYPING_MERGE_MS = 900;
    let TEXT_BLOCK_TYPES = {
        paragraph: true,
        heading1: true,
        heading2: true,
        heading3: true,
        quote: true,
        code: true,
        unordered: true,
        ordered: true
    };
    let MARK_NAMES = ["bold", "italic", "underline", "strike", "code"];

    function cloneValue(value) {
        return JSON.parse(JSON.stringify(value));
    }

    function clamp(value, minimum, maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    function trimText(value) {
        return String(value == null ? "" : value).replace(/^\s+|\s+$/g, "");
    }

    function escapeHtml(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function safeUrl(value, imageMode) {
        let url = trimText(value);
        let lower = url.toLowerCase();
        if (!url) return "";
        if (url.charAt(0) === "#" || url.charAt(0) === "/" || url.indexOf("./") === 0 || url.indexOf("../") === 0) {
            return url;
        }
        if (lower.indexOf("https://") === 0 || lower.indexOf("http://") === 0) return url;
        if (!imageMode && (lower.indexOf("mailto:") === 0 || lower.indexOf("tel:") === 0)) return url;
        if (imageMode && lower.indexOf("data:image/") === 0) return url;
        return "";
    }

    function createMarks(source) {
        let marks = {
            bold: false,
            italic: false,
            underline: false,
            strike: false,
            code: false,
            color: "",
            background: "",
            link: ""
        };
        let name;
        if (!source) return marks;
        for (name in marks) {
            if (Object.prototype.hasOwnProperty.call(marks, name) && source[name] != null) {
                marks[name] = source[name];
            }
        }
        return marks;
    }

    function sameMarks(first, second) {
        let name;
        let left = createMarks(first);
        let right = createMarks(second);
        for (name in left) {
            if (Object.prototype.hasOwnProperty.call(left, name) && left[name] !== right[name]) return false;
        }
        return true;
    }

    function createRun(text, marks) {
        return { text: String(text == null ? "" : text), marks: createMarks(marks) };
    }

    function createBlock(type, runs) {
        let actualType = TEXT_BLOCK_TYPES[type] ? type : "paragraph";
        return {
            type: actualType,
            align: "left",
            indent: 0,
            runs: normalizeRuns(runs || [])
        };
    }

    function createAtomicBlock(type, source, alt) {
        if (type === "divider") {
            return { type: "divider", align: "center", indent: 0, runs: [] };
        }
        return {
            type: "image",
            align: "center",
            indent: 0,
            src: safeUrl(source, true),
            alt: String(alt == null ? "" : alt),
            runs: []
        };
    }

    function isTextBlock(block) {
        return !!(block && TEXT_BLOCK_TYPES[block.type]);
    }

    function isAtomicBlock(block) {
        return !!(block && (block.type === "image" || block.type === "divider"));
    }

    function normalizeRuns(runs) {
        let result = [];
        let index;
        let current;
        let previous;
        for (index = 0; index < runs.length; index += 1) {
            current = createRun(runs[index].text, runs[index].marks);
            if (!current.text) continue;
            previous = result.length ? result[result.length - 1] : null;
            if (previous && sameMarks(previous.marks, current.marks)) {
                previous.text += current.text;
            } else {
                result.push(current);
            }
        }
        return result;
    }

    function blockLength(block) {
        let total = 0;
        let index;
        if (!block) return 0;
        if (isAtomicBlock(block)) return 1;
        for (index = 0; index < block.runs.length; index += 1) {
            total += block.runs[index].text.length;
        }
        return total;
    }

    function blockText(block) {
        let text = "";
        let index;
        if (!block) return text;
        if (block.type === "image") return block.alt || "";
        if (block.type === "divider") return "";
        for (index = 0; index < block.runs.length; index += 1) text += block.runs[index].text;
        return text;
    }

    function splitRuns(runs, offset) {
        let left = [];
        let right = [];
        let cursor = 0;
        let index;
        let run;
        let runEnd;
        let local;
        for (index = 0; index < runs.length; index += 1) {
            run = runs[index];
            runEnd = cursor + run.text.length;
            if (offset <= cursor) {
                right.push(createRun(run.text, run.marks));
            } else if (offset >= runEnd) {
                left.push(createRun(run.text, run.marks));
            } else {
                local = offset - cursor;
                if (local > 0) left.push(createRun(run.text.substring(0, local), run.marks));
                if (local < run.text.length) right.push(createRun(run.text.substring(local), run.marks));
            }
            cursor = runEnd;
        }
        return [normalizeRuns(left), normalizeRuns(right)];
    }

    function sliceRuns(runs, start, end) {
        let tail = splitRuns(runs, start)[1];
        return splitRuns(tail, Math.max(0, end - start))[0];
    }

    function joinRuns(first, second) {
        return normalizeRuns((first || []).concat(second || []));
    }

    function pointBefore(first, second) {
        return first.block < second.block || (first.block === second.block && first.offset <= second.offset);
    }

    function samePoint(first, second) {
        return first && second && first.block === second.block && first.offset === second.offset;
    }

    function marksAt(block, offset) {
        let cursor = 0;
        let index;
        let run;
        let end;
        if (!isTextBlock(block) || !block.runs.length) return createMarks();
        for (index = 0; index < block.runs.length; index += 1) {
            run = block.runs[index];
            end = cursor + run.text.length;
            if (offset > cursor && offset <= end) return createMarks(run.marks);
            if (offset === cursor && index === 0) return createMarks(run.marks);
            cursor = end;
        }
        return createMarks(block.runs[block.runs.length - 1].marks);
    }

    function runMarkEnabled(run, name) {
        if (!run || !run.marks) return false;
        if (name === "color" || name === "background" || name === "link") return !!run.marks[name];
        return run.marks[name] === true;
    }

    function EditorModel() {
        this.state = this.createSampleState();
        this.undoStack = [];
        this.redoStack = [];
    }

    EditorModel.prototype.createSampleState = function () {
        let bold = createMarks({ bold: true });
        let code = createMarks({ code: true });
        let link = createMarks({ link: "https://example.com" });
        return {
            title: "远峰矿区勘探记录",
            blocks: [
                createBlock("heading1", [createRun("远峰矿区勘探记录")]),
                createBlock("paragraph", [
                    createRun("第 7 勘探队于霜降日前抵达北侧矿脉。主矿层由 "),
                    createRun("深板岩", bold),
                    createRun(" 与辉石构成，样本编号 "),
                    createRun("ORE-07-A", code),
                    createRun(" 已封存。")
                ]),
                createBlock("quote", [createRun("岩层稳定，但下层空洞仍需支护后再进入。")]),
                createBlock("heading2", [createRun("今日记录")]),
                createBlock("unordered", [createRun("完成东侧主巷道测绘")]),
                createBlock("unordered", [createRun("回收三组矿芯样本")]),
                createBlock("unordered", [createRun("更新 "), createRun("共享坐标", link), createRun(" 与警戒标记")]),
                createBlock("paragraph", [createRun("下一班次计划继续向北推进 18 米。")])
            ],
            selection: {
                anchor: { block: 0, offset: 0 },
                focus: { block: 0, offset: 0 }
            },
            storedMarks: createMarks(),
            mode: "edit"
        };
    };

    EditorModel.prototype.createBlankState = function () {
        return {
            title: "未命名文档",
            blocks: [createBlock("paragraph", [])],
            selection: {
                anchor: { block: 0, offset: 0 },
                focus: { block: 0, offset: 0 }
            },
            storedMarks: createMarks(),
            mode: "edit"
        };
    };

    EditorModel.prototype.snapshot = function () {
        return cloneValue({
            title: this.state.title,
            blocks: this.state.blocks,
            selection: this.state.selection,
            storedMarks: this.state.storedMarks
        });
    };

    EditorModel.prototype.restore = function (snapshot) {
        let mode = this.state.mode;
        this.state = cloneValue(snapshot);
        this.state.mode = mode;
        this.normalize();
    };

    EditorModel.prototype.normalize = function () {
        let normalized = [];
        let index;
        let block;
        for (index = 0; index < this.state.blocks.length; index += 1) {
            block = this.state.blocks[index];
            if (!block || (!isTextBlock(block) && !isAtomicBlock(block))) continue;
            block.align = block.align === "center" || block.align === "right" || block.align === "justify" ? block.align : "left";
            block.indent = clamp(parseInt(block.indent, 10) || 0, 0, 6);
            if (isTextBlock(block)) block.runs = normalizeRuns(block.runs || []);
            if (block.type === "image") {
                block.src = safeUrl(block.src, true);
                block.alt = String(block.alt == null ? "" : block.alt);
                if (!block.src) continue;
            }
            normalized.push(block);
        }
        if (!normalized.length) normalized.push(createBlock("paragraph", []));
        this.state.blocks = normalized;
        this.state.selection.anchor = this.clampPoint(this.state.selection.anchor);
        this.state.selection.focus = this.clampPoint(this.state.selection.focus);
        this.state.storedMarks = createMarks(this.state.storedMarks);
    };

    EditorModel.prototype.clampPoint = function (point) {
        let blockIndex = clamp(point && isFinite(point.block) ? point.block : 0, 0, this.state.blocks.length - 1);
        let maximum = blockLength(this.state.blocks[blockIndex]);
        return {
            block: blockIndex,
            offset: clamp(point && isFinite(point.offset) ? point.offset : 0, 0, maximum)
        };
    };

    EditorModel.prototype.range = function () {
        let anchor = this.clampPoint(this.state.selection.anchor);
        let focus = this.clampPoint(this.state.selection.focus);
        if (pointBefore(anchor, focus)) return { start: anchor, end: focus, reversed: false };
        return { start: focus, end: anchor, reversed: true };
    };

    EditorModel.prototype.isCollapsed = function () {
        return samePoint(this.state.selection.anchor, this.state.selection.focus);
    };

    EditorModel.prototype.setSelection = function (anchor, focus, updateMarks) {
        let previousAnchor = this.state.selection.anchor;
        let previousFocus = this.state.selection.focus;
        let nextAnchor = this.clampPoint(anchor);
        let nextFocus = this.clampPoint(focus || anchor);
        let moved = !samePoint(previousAnchor, nextAnchor) || !samePoint(previousFocus, nextFocus);
        this.state.selection = { anchor: nextAnchor, focus: nextFocus };
        if (updateMarks && moved && samePoint(nextAnchor, nextFocus)) {
            this.state.storedMarks = marksAt(this.state.blocks[nextAnchor.block], nextAnchor.offset);
        }
    };

    EditorModel.prototype.collapse = function (point) {
        let actual = this.clampPoint(point);
        this.state.selection = { anchor: actual, focus: cloneValue(actual) };
        this.state.storedMarks = marksAt(this.state.blocks[actual.block], actual.offset);
    };

    EditorModel.prototype.selectedBlockIndexes = function () {
        let range = this.range();
        let endBlock = range.end.block;
        let result = [];
        let index;
        if (range.end.offset === 0 && endBlock > range.start.block) endBlock -= 1;
        for (index = range.start.block; index <= endBlock; index += 1) result.push(index);
        if (!result.length) result.push(range.start.block);
        return result;
    };

    EditorModel.prototype.deleteSelection = function () {
        let range;
        let first;
        let last;
        let left;
        let right;
        let replacement;
        if (this.isCollapsed()) return false;
        range = this.range();
        first = this.state.blocks[range.start.block];
        last = this.state.blocks[range.end.block];
        if (range.start.block === range.end.block) {
            if (isAtomicBlock(first)) {
                this.state.blocks.splice(range.start.block, 1);
                if (!this.state.blocks.length) this.state.blocks.push(createBlock("paragraph", []));
                this.collapse({ block: Math.min(range.start.block, this.state.blocks.length - 1), offset: 0 });
                return true;
            }
            left = splitRuns(first.runs, range.start.offset)[0];
            right = splitRuns(first.runs, range.end.offset)[1];
            first.runs = joinRuns(left, right);
            this.collapse(range.start);
            return true;
        }
        left = isTextBlock(first) ? splitRuns(first.runs, range.start.offset)[0] : [];
        right = isTextBlock(last) ? splitRuns(last.runs, range.end.offset)[1] : [];
        replacement = createBlock(isTextBlock(first) ? first.type : "paragraph", joinRuns(left, right));
        replacement.align = isTextBlock(first) ? first.align : "left";
        replacement.indent = isTextBlock(first) ? first.indent : 0;
        this.state.blocks.splice(range.start.block, range.end.block - range.start.block + 1, replacement);
        this.collapse({ block: range.start.block, offset: blockLength({ type: replacement.type, runs: left }) });
        return true;
    };

    EditorModel.prototype.insertText = function (text) {
        let value = String(text == null ? "" : text);
        let point;
        let block;
        let parts;
        let inserted;
        if (!value) return false;
        if (value.indexOf("\n") >= 0 && value !== "\n") return this.insertPlainText(value);
        this.deleteSelection();
        point = this.range().start;
        block = this.state.blocks[point.block];
        if (!isTextBlock(block)) {
            block = createBlock("paragraph", []);
            this.state.blocks.splice(point.block + 1, 0, block);
            point = { block: point.block + 1, offset: 0 };
        }
        parts = splitRuns(block.runs, point.offset);
        inserted = [createRun(value, this.state.storedMarks)];
        block.runs = joinRuns(joinRuns(parts[0], inserted), parts[1]);
        this.collapse({ block: point.block, offset: point.offset + value.length });
        this.state.storedMarks = createMarks(inserted[0].marks);
        return true;
    };

    EditorModel.prototype.insertPlainText = function (text) {
        let lines = String(text == null ? "" : text).replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
        let changed = false;
        let index;
        if (!lines.length) return false;
        if (lines[0]) changed = this.insertText(lines[0]) || changed;
        for (index = 1; index < lines.length; index += 1) {
            changed = this.insertParagraph(false) || changed;
            if (lines[index]) changed = this.insertText(lines[index]) || changed;
        }
        return changed;
    };

    EditorModel.prototype.insertParagraph = function (lineBreak) {
        let point;
        let block;
        let parts;
        let nextType;
        let next;
        this.deleteSelection();
        point = this.range().start;
        block = this.state.blocks[point.block];
        if (!isTextBlock(block)) {
            this.state.blocks.splice(point.block + 1, 0, createBlock("paragraph", []));
            this.collapse({ block: point.block + 1, offset: 0 });
            return true;
        }
        if (lineBreak || block.type === "code") return this.insertText("\n");
        if ((block.type === "unordered" || block.type === "ordered") && blockLength(block) === 0) {
            block.type = "paragraph";
            block.indent = 0;
            this.collapse(point);
            return true;
        }
        parts = splitRuns(block.runs, point.offset);
        block.runs = parts[0];
        nextType = block.type;
        if (nextType === "heading1" || nextType === "heading2" || nextType === "heading3" || nextType === "quote") {
            nextType = "paragraph";
        }
        next = createBlock(nextType, parts[1]);
        next.align = block.align;
        next.indent = block.indent;
        this.state.blocks.splice(point.block + 1, 0, next);
        this.collapse({ block: point.block + 1, offset: 0 });
        return true;
    };

    EditorModel.prototype.deleteBackward = function () {
        let point;
        let block;
        let previous;
        let previousLength;
        if (!this.isCollapsed()) return this.deleteSelection();
        point = this.range().start;
        block = this.state.blocks[point.block];
        if (isTextBlock(block) && point.offset > 0) {
            this.setSelection({ block: point.block, offset: point.offset - 1 }, point, false);
            return this.deleteSelection();
        }
        if (point.block <= 0) return false;
        previous = this.state.blocks[point.block - 1];
        if (isAtomicBlock(previous)) {
            this.state.blocks.splice(point.block - 1, 1);
            this.collapse({ block: point.block - 1, offset: 0 });
            return true;
        }
        if (!isTextBlock(block) || !isTextBlock(previous)) return false;
        previousLength = blockLength(previous);
        previous.runs = joinRuns(previous.runs, block.runs);
        this.state.blocks.splice(point.block, 1);
        this.collapse({ block: point.block - 1, offset: previousLength });
        return true;
    };

    EditorModel.prototype.deleteForward = function () {
        let point;
        let block;
        let next;
        if (!this.isCollapsed()) return this.deleteSelection();
        point = this.range().start;
        block = this.state.blocks[point.block];
        if (isTextBlock(block) && point.offset < blockLength(block)) {
            this.setSelection(point, { block: point.block, offset: point.offset + 1 }, false);
            return this.deleteSelection();
        }
        if (point.block >= this.state.blocks.length - 1) return false;
        next = this.state.blocks[point.block + 1];
        if (isAtomicBlock(next)) {
            this.state.blocks.splice(point.block + 1, 1);
            this.collapse(point);
            return true;
        }
        if (!isTextBlock(block) || !isTextBlock(next)) return false;
        block.runs = joinRuns(block.runs, next.runs);
        this.state.blocks.splice(point.block + 1, 1);
        this.collapse(point);
        return true;
    };

    EditorModel.prototype.selectAll = function () {
        let last = this.state.blocks.length - 1;
        this.setSelection({ block: 0, offset: 0 }, { block: last, offset: blockLength(this.state.blocks[last]) }, false);
    };

    EditorModel.prototype.transformRuns = function (block, start, end, callback) {
        let first = splitRuns(block.runs, start);
        let middleAndTail = splitRuns(first[1], Math.max(0, end - start));
        let middle = middleAndTail[0];
        let index;
        for (index = 0; index < middle.length; index += 1) callback(middle[index].marks);
        block.runs = joinRuns(joinRuns(first[0], middle), middleAndTail[1]);
    };

    EditorModel.prototype.markState = function (name) {
        let range;
        let found = false;
        let active = false;
        let inactive = false;
        let index;
        let block;
        let start;
        let end;
        let runs;
        let runIndex;
        if (this.isCollapsed()) return this.state.storedMarks[name] ? 2 : 0;
        range = this.range();
        for (index = range.start.block; index <= range.end.block; index += 1) {
            block = this.state.blocks[index];
            if (!isTextBlock(block)) continue;
            start = index === range.start.block ? range.start.offset : 0;
            end = index === range.end.block ? range.end.offset : blockLength(block);
            runs = sliceRuns(block.runs, start, end);
            for (runIndex = 0; runIndex < runs.length; runIndex += 1) {
                if (!runs[runIndex].text) continue;
                found = true;
                if (runMarkEnabled(runs[runIndex], name)) active = true;
                else inactive = true;
            }
        }
        if (!found || !active) return 0;
        return inactive ? 1 : 2;
    };

    EditorModel.prototype.applyMark = function (name, value) {
        let range;
        let target;
        let index;
        let block;
        let start;
        let end;
        if (this.isCollapsed()) {
            if (name === "color" || name === "background" || name === "link") {
                this.state.storedMarks[name] = value || "";
            } else {
                this.state.storedMarks[name] = !this.state.storedMarks[name];
            }
            return true;
        }
        range = this.range();
        target = name === "color" || name === "background" || name === "link" ? value || "" : this.markState(name) !== 2;
        for (index = range.start.block; index <= range.end.block; index += 1) {
            block = this.state.blocks[index];
            if (!isTextBlock(block)) continue;
            start = index === range.start.block ? range.start.offset : 0;
            end = index === range.end.block ? range.end.offset : blockLength(block);
            this.transformRuns(block, start, end, function (marks) {
                marks[name] = target;
            });
        }
        return true;
    };

    EditorModel.prototype.removeLink = function () {
        let range;
        let index;
        let block;
        let start;
        let end;
        if (this.isCollapsed()) {
            this.state.storedMarks.link = "";
            return true;
        }
        range = this.range();
        for (index = range.start.block; index <= range.end.block; index += 1) {
            block = this.state.blocks[index];
            if (!isTextBlock(block)) continue;
            start = index === range.start.block ? range.start.offset : 0;
            end = index === range.end.block ? range.end.offset : blockLength(block);
            this.transformRuns(block, start, end, function (marks) {
                marks.link = "";
            });
        }
        return true;
    };

    EditorModel.prototype.clearFormatting = function () {
        let range;
        let index;
        let block;
        let start;
        let end;
        if (this.isCollapsed()) {
            this.state.storedMarks = createMarks();
            return true;
        }
        range = this.range();
        for (index = range.start.block; index <= range.end.block; index += 1) {
            block = this.state.blocks[index];
            if (!isTextBlock(block)) continue;
            start = index === range.start.block ? range.start.offset : 0;
            end = index === range.end.block ? range.end.offset : blockLength(block);
            this.transformRuns(block, start, end, function (marks) {
                let cleared = createMarks();
                let name;
                for (name in cleared) {
                    if (Object.prototype.hasOwnProperty.call(cleared, name)) marks[name] = cleared[name];
                }
            });
        }
        return true;
    };

    EditorModel.prototype.setBlockType = function (type) {
        let indexes = this.selectedBlockIndexes();
        let listToggle = type === "unordered" || type === "ordered";
        let allSame = true;
        let index;
        let block;
        for (index = 0; index < indexes.length; index += 1) {
            block = this.state.blocks[indexes[index]];
            if (!isTextBlock(block) || block.type !== type) allSame = false;
        }
        for (index = 0; index < indexes.length; index += 1) {
            block = this.state.blocks[indexes[index]];
            if (!isTextBlock(block)) continue;
            block.type = listToggle && allSame ? "paragraph" : type;
            if (block.type !== "unordered" && block.type !== "ordered") block.indent = 0;
        }
        return true;
    };

    EditorModel.prototype.setAlignment = function (alignment) {
        let indexes = this.selectedBlockIndexes();
        let index;
        for (index = 0; index < indexes.length; index += 1) {
            this.state.blocks[indexes[index]].align = alignment;
        }
        return true;
    };

    EditorModel.prototype.changeIndent = function (amount) {
        let indexes = this.selectedBlockIndexes();
        let index;
        let block;
        let changed = false;
        for (index = 0; index < indexes.length; index += 1) {
            block = this.state.blocks[indexes[index]];
            if (!isTextBlock(block)) continue;
            if (block.type !== "unordered" && block.type !== "ordered" && block.type !== "quote") continue;
            block.indent = clamp(block.indent + amount, 0, 6);
            changed = true;
        }
        return changed;
    };

    EditorModel.prototype.insertAtomic = function (type, source, alt) {
        let point;
        let block;
        let parts;
        let before;
        let after;
        let atomic;
        this.deleteSelection();
        point = this.range().start;
        block = this.state.blocks[point.block];
        atomic = createAtomicBlock(type, source, alt);
        if (type === "image" && !atomic.src) return false;
        if (!isTextBlock(block)) {
            this.state.blocks.splice(point.block + 1, 0, atomic, createBlock("paragraph", []));
            this.collapse({ block: point.block + 2, offset: 0 });
            return true;
        }
        parts = splitRuns(block.runs, point.offset);
        before = createBlock(block.type, parts[0]);
        before.align = block.align;
        before.indent = block.indent;
        after = createBlock(block.type === "unordered" || block.type === "ordered" ? block.type : "paragraph", parts[1]);
        after.align = block.align;
        after.indent = block.indent;
        this.state.blocks.splice(point.block, 1, before, atomic, after);
        this.collapse({ block: point.block + 2, offset: 0 });
        return true;
    };

    EditorModel.prototype.insertFragment = function (fragmentBlocks) {
        let fragments = cloneValue(fragmentBlocks || []);
        let point;
        let current;
        let parts;
        let first;
        let last;
        let replacement = [];
        let cursorBlock;
        let cursorOffset;
        let index;
        if (!fragments.length) return false;
        this.deleteSelection();
        point = this.range().start;
        current = this.state.blocks[point.block];
        if (fragments.length === 1 && isTextBlock(fragments[0]) && fragments[0].type === "paragraph" && isTextBlock(current)) {
            parts = splitRuns(current.runs, point.offset);
            current.runs = joinRuns(joinRuns(parts[0], fragments[0].runs), parts[1]);
            this.collapse({ block: point.block, offset: point.offset + blockLength(fragments[0]) });
            return true;
        }
        if (!isTextBlock(current)) {
            this.state.blocks.splice.apply(this.state.blocks, [point.block + 1, 0].concat(fragments));
            last = fragments[fragments.length - 1];
            cursorBlock = point.block + fragments.length;
            cursorOffset = blockLength(last);
            this.collapse({ block: cursorBlock, offset: cursorOffset });
            return true;
        }
        parts = splitRuns(current.runs, point.offset);
        first = fragments[0];
        if (isTextBlock(first)) {
            first.runs = joinRuns(parts[0], first.runs);
            first.type = current.type;
            first.align = current.align;
            first.indent = current.indent;
        } else {
            replacement.push(createBlock(current.type, parts[0]));
        }
        for (index = 0; index < fragments.length; index += 1) replacement.push(fragments[index]);
        last = replacement[replacement.length - 1];
        if (isTextBlock(last)) {
            cursorBlock = point.block + replacement.length - 1;
            cursorOffset = blockLength(last);
            last.runs = joinRuns(last.runs, parts[1]);
        } else {
            replacement.push(createBlock("paragraph", parts[1]));
            cursorBlock = point.block + replacement.length - 1;
            cursorOffset = 0;
        }
        this.state.blocks.splice.apply(this.state.blocks, [point.block, 1].concat(replacement));
        this.collapse({ block: cursorBlock, offset: cursorOffset });
        return true;
    };

    EditorModel.prototype.selectedBlocks = function () {
        let range = this.range();
        let result = [];
        let index;
        let block;
        let start;
        let end;
        let copy;
        if (this.isCollapsed()) return result;
        for (index = range.start.block; index <= range.end.block; index += 1) {
            block = this.state.blocks[index];
            start = index === range.start.block ? range.start.offset : 0;
            end = index === range.end.block ? range.end.offset : blockLength(block);
            if (isAtomicBlock(block)) {
                if (start === 0 && end === 1) result.push(cloneValue(block));
                continue;
            }
            if (end <= start) continue;
            copy = cloneValue(block);
            copy.runs = sliceRuns(block.runs, start, end);
            result.push(copy);
        }
        return result;
    };

    EditorModel.prototype.selectedText = function () {
        let blocks = this.selectedBlocks();
        let parts = [];
        let index;
        for (index = 0; index < blocks.length; index += 1) parts.push(blockText(blocks[index]));
        return parts.join("\n");
    };

    EditorModel.prototype.plainText = function () {
        let parts = [];
        let index;
        for (index = 0; index < this.state.blocks.length; index += 1) parts.push(blockText(this.state.blocks[index]));
        return parts.join("\n");
    };

    function runToHtml(run) {
        let marks = createMarks(run.marks);
        let html = escapeHtml(run.text).replace(/\n/g, "<br>");
        if (marks.code) html = "<code>" + html + "</code>";
        if (marks.bold) html = "<strong>" + html + "</strong>";
        if (marks.italic) html = "<em>" + html + "</em>";
        if (marks.underline) html = "<u>" + html + "</u>";
        if (marks.strike) html = "<s>" + html + "</s>";
        if (marks.color || marks.background) {
            html = "<span style=\"" + (marks.color ? "color:" + escapeHtml(marks.color) + ";" : "")
                + (marks.background ? "background-color:" + escapeHtml(marks.background) + ";" : "") + "\">" + html + "</span>";
        }
        if (marks.link && safeUrl(marks.link, false)) {
            html = "<a href=\"" + escapeHtml(safeUrl(marks.link, false)) + "\">" + html + "</a>";
        }
        return html;
    }

    function runsToHtml(runs) {
        let html = "";
        let index;
        for (index = 0; index < runs.length; index += 1) html += runToHtml(runs[index]);
        return html || "<br>";
    }

    function blockStyle(block) {
        let style = "";
        if (block.align && block.align !== "left") style += "text-align:" + block.align + ";";
        if (block.indent) style += "margin-left:" + (block.indent * 24) + "px;";
        return style ? " style=\"" + style + "\"" : "";
    }

    function blocksToHtml(blocks) {
        let html = "";
        let index = 0;
        let block;
        let tag;
        let listType;
        while (index < blocks.length) {
            block = blocks[index];
            if (block.type === "unordered" || block.type === "ordered") {
                listType = block.type;
                tag = listType === "unordered" ? "ul" : "ol";
                html += "<" + tag + ">\n";
                while (index < blocks.length && blocks[index].type === listType) {
                    block = blocks[index];
                    html += "  <li" + blockStyle(block) + ">" + runsToHtml(block.runs) + "</li>\n";
                    index += 1;
                }
                html += "</" + tag + ">\n";
                continue;
            }
            if (block.type === "image") {
                html += "<figure" + blockStyle(block) + "><img src=\"" + escapeHtml(safeUrl(block.src, true)) + "\" alt=\""
                    + escapeHtml(block.alt) + "\">" + (block.alt ? "<figcaption>" + escapeHtml(block.alt) + "</figcaption>" : "") + "</figure>\n";
            } else if (block.type === "divider") {
                html += "<hr>\n";
            } else if (block.type === "heading1" || block.type === "heading2" || block.type === "heading3") {
                tag = "h" + block.type.substring(block.type.length - 1);
                html += "<" + tag + blockStyle(block) + ">" + runsToHtml(block.runs) + "</" + tag + ">\n";
            } else if (block.type === "quote") {
                html += "<blockquote" + blockStyle(block) + ">" + runsToHtml(block.runs) + "</blockquote>\n";
            } else if (block.type === "code") {
                html += "<pre" + blockStyle(block) + "><code>" + escapeHtml(blockText(block)) + "</code></pre>\n";
            } else {
                html += "<p" + blockStyle(block) + ">" + runsToHtml(block.runs) + "</p>\n";
            }
            index += 1;
        }
        return html;
    }

    function applyNodeMarks(node, inherited) {
        let marks = createMarks(inherited);
        let tag = node && node.nodeName ? node.nodeName.toUpperCase() : "";
        let weight;
        let decoration;
        if (tag === "B" || tag === "STRONG") marks.bold = true;
        if (tag === "I" || tag === "EM") marks.italic = true;
        if (tag === "U") marks.underline = true;
        if (tag === "S" || tag === "STRIKE" || tag === "DEL") marks.strike = true;
        if (tag === "CODE") marks.code = true;
        if (tag === "A") marks.link = safeUrl(node.getAttribute("href"), false);
        if (node && node.style) {
            weight = String(node.style.fontWeight || "").toLowerCase();
            if (weight === "bold" || weight === "bolder" || parseInt(weight, 10) >= 600) marks.bold = true;
            if (String(node.style.fontStyle || "").toLowerCase() === "italic") marks.italic = true;
            decoration = String(node.style.textDecoration || node.style.textDecorationLine || "").toLowerCase();
            if (decoration.indexOf("underline") >= 0) marks.underline = true;
            if (decoration.indexOf("line-through") >= 0) marks.strike = true;
            if (node.style.color) marks.color = node.style.color;
            if (node.style.backgroundColor) marks.background = node.style.backgroundColor;
        }
        return marks;
    }

    function parseInlineNode(node, inherited, runs) {
        let marks;
        let index;
        let tag;
        if (!node) return;
        if (node.nodeType === 3) {
            if (node.nodeValue) runs.push(createRun(node.nodeValue, inherited));
            return;
        }
        if (node.nodeType !== 1) return;
        tag = node.nodeName.toUpperCase();
        if (tag === "SCRIPT" || tag === "STYLE" || tag === "IFRAME" || tag === "OBJECT") return;
        if (tag === "BR") {
            runs.push(createRun("\n", inherited));
            return;
        }
        marks = applyNodeMarks(node, inherited);
        for (index = 0; index < node.childNodes.length; index += 1) parseInlineNode(node.childNodes[index], marks, runs);
    }

    function parseInlineChildren(element) {
        let runs = [];
        let index;
        for (index = 0; index < element.childNodes.length; index += 1) {
            parseInlineNode(element.childNodes[index], createMarks(), runs);
        }
        return normalizeRuns(runs);
    }

    function alignmentFromElement(element) {
        let value = element && element.style ? String(element.style.textAlign || "").toLowerCase() : "";
        return value === "center" || value === "right" || value === "justify" ? value : "left";
    }

    function parseHtmlBlocks(html) {
        let container = document.createElement("div");
        let blocks = [];
        let looseRuns = [];
        let index;
        let node;
        let tag;
        let block;
        let itemIndex;
        let image;
        let caption;

        function flushLoose() {
            let text = "";
            let runIndex;
            for (runIndex = 0; runIndex < looseRuns.length; runIndex += 1) text += looseRuns[runIndex].text;
            if (text || looseRuns.length) blocks.push(createBlock("paragraph", looseRuns));
            looseRuns = [];
        }

        container.innerHTML = String(html == null ? "" : html);
        for (index = 0; index < container.childNodes.length; index += 1) {
            node = container.childNodes[index];
            if (node.nodeType === 3) {
                if (trimText(node.nodeValue)) parseInlineNode(node, createMarks(), looseRuns);
                continue;
            }
            if (node.nodeType !== 1) continue;
            tag = node.nodeName.toUpperCase();
            if (tag === "SCRIPT" || tag === "STYLE" || tag === "IFRAME" || tag === "OBJECT") continue;
            if (tag === "UL" || tag === "OL") {
                flushLoose();
                for (itemIndex = 0; itemIndex < node.children.length; itemIndex += 1) {
                    if (node.children[itemIndex].nodeName.toUpperCase() !== "LI") continue;
                    block = createBlock(tag === "UL" ? "unordered" : "ordered", parseInlineChildren(node.children[itemIndex]));
                    block.align = alignmentFromElement(node.children[itemIndex]);
                    blocks.push(block);
                }
            } else if (tag === "H1" || tag === "H2" || tag === "H3") {
                flushLoose();
                block = createBlock("heading" + tag.substring(1), parseInlineChildren(node));
                block.align = alignmentFromElement(node);
                blocks.push(block);
            } else if (tag === "BLOCKQUOTE") {
                flushLoose();
                block = createBlock("quote", parseInlineChildren(node));
                block.align = alignmentFromElement(node);
                blocks.push(block);
            } else if (tag === "PRE") {
                flushLoose();
                block = createBlock("code", [createRun(node.textContent || "")]);
                block.align = alignmentFromElement(node);
                blocks.push(block);
            } else if (tag === "P" || tag === "DIV" || tag === "SECTION" || tag === "ARTICLE") {
                flushLoose();
                block = createBlock("paragraph", parseInlineChildren(node));
                block.align = alignmentFromElement(node);
                blocks.push(block);
            } else if (tag === "HR") {
                flushLoose();
                blocks.push(createAtomicBlock("divider"));
            } else if (tag === "IMG") {
                flushLoose();
                image = createAtomicBlock("image", node.getAttribute("src"), node.getAttribute("alt"));
                if (image.src) blocks.push(image);
            } else if (tag === "FIGURE") {
                flushLoose();
                image = node.querySelector("img");
                caption = node.querySelector("figcaption");
                if (image) {
                    block = createAtomicBlock("image", image.getAttribute("src"), caption ? caption.textContent : image.getAttribute("alt"));
                    if (block.src) blocks.push(block);
                }
            } else {
                parseInlineNode(node, createMarks(), looseRuns);
            }
        }
        flushLoose();
        return blocks;
    }

    function standaloneHtml(model) {
        return "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n"
            + "  <meta charset=\"utf-8\">\n"
            + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
            + "  <title>" + escapeHtml(model.state.title) + "</title>\n"
            + "  <link rel=\"stylesheet\" href=\"/apricityui/theme/ore/ore.css\">\n"
            + "  <style>body{background:var(--ore-canvas)}.document{max-width:820px;margin:0 auto;padding:32px 24px}.document figure{text-align:center}</style>\n"
            + "</head>\n<body class=\"ore-theme\">\n<main class=\"document\">\n"
            + blocksToHtml(model.state.blocks)
            + "</main>\n</body>\n</html>\n";
    }

    function EditorView(model) {
        this.model = model;
        this.surface = document.getElementById("editorSurface");
        this.preview = document.getElementById("previewSurface");
        this.source = document.getElementById("sourceEditor");
        this.sourceState = document.getElementById("sourceState");
        this.toolbar = document.getElementById("formatToolbar");
        this.blockType = document.getElementById("blockType");
        this.title = document.getElementById("documentTitle");
        this.outline = document.getElementById("documentOutline");
        this.saveState = document.getElementById("saveState");
        this.rendering = false;
        this.sourceDirty = false;
    }

    EditorView.prototype.createBlockElement = function (block, index, tag) {
        let element = document.createElement(tag);
        element.className = "editor-block";
        element.setAttribute("data-block-index", String(index));
        element.style.textAlign = block.align || "left";
        if (block.indent) element.style.marginLeft = (block.indent * 24) + "px";
        return element;
    };

    EditorView.prototype.renderRuns = function (element, block) {
        let cursor = 0;
        let index;
        let run;
        let span;
        let classes;
        if (!block.runs.length) {
            element.setAttribute("data-empty", "true");
            element.appendChild(document.createElement("br"));
            return;
        }
        for (index = 0; index < block.runs.length; index += 1) {
            run = block.runs[index];
            span = document.createElement("span");
            classes = ["editor-run"];
            if (run.marks.bold) classes.push("mark-bold");
            if (run.marks.italic) classes.push("mark-italic");
            if (run.marks.underline) classes.push("mark-underline");
            if (run.marks.strike) classes.push("mark-strike");
            if (run.marks.code) classes.push("mark-code");
            if (run.marks.link) classes.push("mark-link");
            span.className = classes.join(" ");
            span.setAttribute("data-run-start", String(cursor));
            if (run.marks.color) span.style.color = run.marks.color;
            if (run.marks.background) span.style.backgroundColor = run.marks.background;
            if (run.marks.link) span.setAttribute("data-link", run.marks.link);
            span.appendChild(document.createTextNode(run.text));
            element.appendChild(span);
            cursor += run.text.length;
        }
    };

    EditorView.prototype.renderTextBlock = function (block, index, tag) {
        let element = this.createBlockElement(block, index, tag);
        this.renderRuns(element, block);
        return element;
    };

    EditorView.prototype.atomicSelected = function (index) {
        let range = this.model.range();
        return !this.model.isCollapsed() && range.start.block === index && range.end.block === index
            && range.start.offset === 0 && range.end.offset === 1;
    };

    EditorView.prototype.renderDocument = function (restoreSelection, focusSurface) {
        let fragment = document.createDocumentFragment();
        let blocks = this.model.state.blocks;
        let index = 0;
        let block;
        let tag;
        let list;
        let item;
        let figure;
        let image;
        let caption;
        let divider;
        this.rendering = true;
        while (this.surface.firstChild) this.surface.removeChild(this.surface.firstChild);
        while (index < blocks.length) {
            block = blocks[index];
            if (block.type === "unordered" || block.type === "ordered") {
                tag = block.type === "unordered" ? "ul" : "ol";
                list = document.createElement(tag);
                list.className = "editor-list";
                while (index < blocks.length && blocks[index].type === block.type) {
                    item = this.renderTextBlock(blocks[index], index, "li");
                    list.appendChild(item);
                    index += 1;
                }
                fragment.appendChild(list);
                continue;
            }
            if (block.type === "image") {
                figure = document.createElement("figure");
                figure.className = "editor-atomic editor-image-block" + (this.atomicSelected(index) ? " selected" : "");
                figure.setAttribute("data-block-index", String(index));
                figure.setAttribute("data-atomic", "true");
                figure.setAttribute("contenteditable", "false");
                figure.style.textAlign = block.align || "center";
                image = document.createElement("img");
                image.setAttribute("src", block.src);
                image.setAttribute("alt", block.alt || "");
                figure.appendChild(image);
                if (block.alt) {
                    caption = document.createElement("figcaption");
                    caption.textContent = block.alt;
                    figure.appendChild(caption);
                }
                fragment.appendChild(figure);
            } else if (block.type === "divider") {
                divider = document.createElement("div");
                divider.className = "editor-atomic editor-divider-block" + (this.atomicSelected(index) ? " selected" : "");
                divider.setAttribute("data-block-index", String(index));
                divider.setAttribute("data-atomic", "true");
                divider.setAttribute("contenteditable", "false");
                divider.appendChild(document.createElement("hr"));
                fragment.appendChild(divider);
            } else {
                tag = "p";
                if (block.type === "heading1") tag = "h1";
                if (block.type === "heading2") tag = "h2";
                if (block.type === "heading3") tag = "h3";
                if (block.type === "quote") tag = "blockquote";
                if (block.type === "code") tag = "pre";
                fragment.appendChild(this.renderTextBlock(block, index, tag));
            }
            index += 1;
        }
        this.surface.appendChild(fragment);
        this.rendering = false;
        if (restoreSelection) this.restoreSelection(focusSurface);
    };

    EditorView.prototype.findBlockElement = function (node) {
        let current = node && node.nodeType === 3 ? node.parentNode : node;
        while (current && current !== this.surface) {
            if (current.nodeType === 1 && current.hasAttribute("data-block-index")) return current;
            current = current.parentNode;
        }
        return null;
    };

    EditorView.prototype.positionFromDom = function (node, offset) {
        let blockElement = this.findBlockElement(node);
        let blockIndex;
        let range;
        let length;
        if (!blockElement) return null;
        blockIndex = parseInt(blockElement.getAttribute("data-block-index"), 10);
        if (blockElement.hasAttribute("data-atomic")) {
            return { block: blockIndex, offset: offset > 0 ? 1 : 0 };
        }
        try {
            range = document.createRange();
            range.setStart(blockElement, 0);
            range.setEnd(node, offset);
            length = range.toString().length;
        } catch (error) {
            length = 0;
        }
        return { block: blockIndex, offset: clamp(length, 0, blockLength(this.model.state.blocks[blockIndex])) };
    };

    EditorView.prototype.domPoint = function (point) {
        let selector = "[data-block-index=\"" + point.block + "\"]";
        let blockElement = this.surface.querySelector(selector);
        let walker;
        let node;
        let remaining = point.offset;
        let lastText = null;
        if (!blockElement) return null;
        if (blockElement.hasAttribute("data-atomic")) return { node: blockElement.parentNode, offset: point.offset ? 1 : 0 };
        walker = document.createTreeWalker(blockElement, window.NodeFilter.SHOW_TEXT, null, false);
        node = walker.nextNode();
        while (node) {
            lastText = node;
            if (remaining <= node.nodeValue.length) return { node: node, offset: remaining };
            remaining -= node.nodeValue.length;
            node = walker.nextNode();
        }
        if (lastText) return { node: lastText, offset: lastText.nodeValue.length };
        return { node: blockElement, offset: 0 };
    };

    EditorView.prototype.captureSelection = function () {
        let selection = window.getSelection ? window.getSelection() : null;
        let anchor;
        let focus;
        if (!selection || !selection.anchorNode || !selection.focusNode) return false;
        if (!this.surface.contains(selection.anchorNode) || !this.surface.contains(selection.focusNode)) return false;
        anchor = this.positionFromDom(selection.anchorNode, selection.anchorOffset);
        focus = this.positionFromDom(selection.focusNode, selection.focusOffset);
        if (!anchor || !focus) return false;
        this.model.setSelection(anchor, focus, true);
        return true;
    };

    EditorView.prototype.restoreSelection = function (focusSurface) {
        let selection = window.getSelection ? window.getSelection() : null;
        let anchor = this.domPoint(this.model.state.selection.anchor);
        let focus = this.domPoint(this.model.state.selection.focus);
        let range;
        let ordered;
        if (!selection || !anchor || !focus) return;
        if (focusSurface) this.surface.focus();
        try {
            selection.removeAllRanges();
            range = document.createRange();
            range.setStart(anchor.node, anchor.offset);
            range.collapse(true);
            selection.addRange(range);
            if (selection.extend) {
                selection.extend(focus.node, focus.offset);
            } else {
                ordered = this.model.range();
                anchor = this.domPoint(ordered.start);
                focus = this.domPoint(ordered.end);
                range = document.createRange();
                range.setStart(anchor.node, anchor.offset);
                range.setEnd(focus.node, focus.offset);
                selection.removeAllRanges();
                selection.addRange(range);
            }
        } catch (error) {
            return;
        }
    };

    EditorView.prototype.renderMode = function () {
        let mode = this.model.state.mode;
        let tabs = document.querySelectorAll("[data-mode]");
        let index;
        document.getElementById("editPane").hidden = mode !== "edit";
        document.getElementById("previewPane").hidden = mode !== "preview";
        document.getElementById("sourcePane").hidden = mode !== "source";
        for (index = 0; index < tabs.length; index += 1) {
            tabs[index].classList.toggle("active", tabs[index].getAttribute("data-mode") === mode);
            tabs[index].setAttribute("aria-selected", tabs[index].getAttribute("data-mode") === mode ? "true" : "false");
        }
        if (mode === "preview") this.preview.innerHTML = blocksToHtml(this.model.state.blocks);
        if (mode === "source" && !this.sourceDirty) this.source.value = blocksToHtml(this.model.state.blocks);
        this.toolbar.hidden = mode !== "edit";
    };

    EditorView.prototype.renderToolbar = function () {
        let markButtons = this.toolbar.querySelectorAll("[data-mark]");
        let alignButtons = this.toolbar.querySelectorAll("[data-align]");
        let indexes = this.model.selectedBlockIndexes();
        let firstBlock = this.model.state.blocks[indexes[0]];
        let sameType = true;
        let index;
        let state;
        for (index = 0; index < markButtons.length; index += 1) {
            state = this.model.markState(markButtons[index].getAttribute("data-mark"));
            markButtons[index].classList.toggle("active", state === 2);
            markButtons[index].setAttribute("aria-pressed", state === 2 ? "true" : "false");
        }
        for (index = 1; index < indexes.length; index += 1) {
            if (this.model.state.blocks[indexes[index]].type !== firstBlock.type) sameType = false;
        }
        if (sameType && isTextBlock(firstBlock)) this.blockType.value = firstBlock.type === "unordered" || firstBlock.type === "ordered" ? "paragraph" : firstBlock.type;
        for (index = 0; index < alignButtons.length; index += 1) {
            state = alignButtons[index].getAttribute("data-align");
            alignButtons[index].classList.toggle("active", firstBlock.align === state);
        }
        document.getElementById("undoButton").disabled = this.model.undoStack.length === 0;
        document.getElementById("redoButton").disabled = this.model.redoStack.length === 0;
    };

    EditorView.prototype.renderOutline = function () {
        let blocks = this.model.state.blocks;
        let fragment = document.createDocumentFragment();
        let count = 0;
        let index;
        let block;
        let button;
        while (this.outline.firstChild) this.outline.removeChild(this.outline.firstChild);
        for (index = 0; index < blocks.length; index += 1) {
            block = blocks[index];
            if (block.type !== "heading1" && block.type !== "heading2" && block.type !== "heading3") continue;
            count += 1;
            button = document.createElement("button");
            button.type = "button";
            button.className = "editor-outline-button level-" + block.type.substring(block.type.length - 1);
            button.setAttribute("data-outline-index", String(index));
            button.textContent = blockText(block) || "未命名标题";
            fragment.appendChild(button);
        }
        if (!count) {
            button = document.createElement("div");
            button.className = "editor-outline-empty";
            button.textContent = "暂无标题";
            fragment.appendChild(button);
        }
        this.outline.appendChild(fragment);
        document.getElementById("headingCount").textContent = String(count);
    };

    EditorView.prototype.renderStats = function () {
        let text = this.model.plainText();
        let characters = text.length;
        let words = text.match(/[A-Za-z0-9_]+|[\u3400-\u9fff]/g);
        let paragraphs = 0;
        let lists = 0;
        let media = 0;
        let index;
        let block;
        let selected = this.model.selectedText();
        for (index = 0; index < this.model.state.blocks.length; index += 1) {
            block = this.model.state.blocks[index];
            if (block.type === "unordered" || block.type === "ordered") lists += 1;
            else if (isAtomicBlock(block)) media += 1;
            else paragraphs += 1;
        }
        document.getElementById("wordCount").textContent = (words ? words.length : 0) + " 字";
        document.getElementById("characterCount").textContent = characters + " 字符";
        document.getElementById("blockCount").textContent = this.model.state.blocks.length + " 块";
        document.getElementById("selectionStatus").textContent = selected ? "已选 " + selected.length + " 字符" : "无选区";
        document.getElementById("paragraphStat").textContent = String(paragraphs);
        document.getElementById("listStat").textContent = String(lists);
        document.getElementById("mediaStat").textContent = String(media);
    };

    EditorView.prototype.renderAll = function (restoreSelection, focusSurface) {
        this.renderDocument(restoreSelection && this.model.state.mode === "edit", focusSurface);
        this.renderMode();
        this.renderToolbar();
        this.renderOutline();
        this.renderStats();
        if (document.activeElement !== this.title) this.title.value = this.model.state.title;
    };

    EditorView.prototype.setSaved = function (saved, message) {
        this.saveState.textContent = message || (saved ? "已在本机保存" : "有未保存更改");
        this.saveState.classList.toggle("dirty", !saved);
    };

    EditorView.prototype.setSourceDirty = function (dirty) {
        this.sourceDirty = dirty;
        this.sourceState.textContent = dirty ? "有未应用更改" : "模型生成";
        this.sourceState.className = dirty ? "text-warning" : "text-muted";
    };

    EditorView.prototype.openDialog = function (config) {
        let backdrop = document.getElementById("editorDialogBackdrop");
        let message = document.getElementById("dialogMessage");
        document.getElementById("dialogTitle").textContent = config.title || "编辑";
        document.getElementById("dialogUrl").value = config.url || "";
        document.getElementById("dialogText").value = config.text || "";
        document.getElementById("dialogAlt").value = config.alt || "";
        document.getElementById("dialogUrlGroup").hidden = config.showUrl === false;
        document.getElementById("dialogTextGroup").hidden = config.showText === false;
        document.getElementById("dialogAltGroup").hidden = !config.showAlt;
        message.hidden = !config.message;
        message.textContent = config.message || "";
        document.getElementById("dialogConfirmButton").textContent = config.confirmText || "确定";
        backdrop.classList.add("open");
        backdrop.setAttribute("aria-hidden", "false");
        if (config.showUrl !== false) document.getElementById("dialogUrl").focus();
        else if (config.showText !== false) document.getElementById("dialogText").focus();
        else document.getElementById("dialogConfirmButton").focus();
    };

    EditorView.prototype.closeDialog = function () {
        let backdrop = document.getElementById("editorDialogBackdrop");
        backdrop.classList.remove("open");
        backdrop.setAttribute("aria-hidden", "true");
    };

    function EditorController(model, view) {
        this.model = model;
        this.view = view;
        this.lastGroup = "";
        this.lastCommitTime = 0;
        this.ignoreSelectionUntil = 0;
        this.composing = false;
        this.compositionSelection = null;
        this.dialogKind = "";
        this.dialogSelection = null;
    }

    EditorController.prototype.saveLocal = function () {
        let payload = {
            version: 1,
            title: this.model.state.title,
            blocks: this.model.state.blocks
        };
        try {
            window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
            this.view.setSaved(true);
        } catch (error) {
            this.view.setSaved(false, "本机保存不可用");
        }
    };

    EditorController.prototype.loadLocal = function () {
        let raw;
        let payload;
        try {
            raw = window.localStorage.getItem(STORAGE_KEY);
            if (!raw) return false;
            payload = JSON.parse(raw);
            if (!payload || payload.version !== 1 || !payload.blocks || !payload.blocks.length) return false;
            this.model.state.title = String(payload.title || "未命名文档");
            this.model.state.blocks = cloneValue(payload.blocks);
            this.model.state.selection = { anchor: { block: 0, offset: 0 }, focus: { block: 0, offset: 0 } };
            this.model.state.storedMarks = createMarks();
            this.model.normalize();
            return true;
        } catch (error) {
            return false;
        }
    };

    EditorController.prototype.emitChange = function () {
        let event;
        try {
            event = document.createEvent("Event");
            event.initEvent("editorchange", true, false);
            this.view.surface.dispatchEvent(event);
        } catch (error) {
            return;
        }
    };

    EditorController.prototype.commit = function (label, mutation, group, skipCapture) {
        let before;
        let changed;
        let now = new Date().getTime();
        let merge = !!group && this.lastGroup === group && now - this.lastCommitTime < TYPING_MERGE_MS;
        if (!skipCapture) this.view.captureSelection();
        before = this.model.snapshot();
        changed = mutation();
        if (!changed) return false;
        this.model.normalize();
        if (!merge) {
            this.model.undoStack.push(before);
            if (this.model.undoStack.length > HISTORY_LIMIT) this.model.undoStack.shift();
        }
        this.model.redoStack = [];
        this.lastGroup = group || label;
        this.lastCommitTime = now;
        this.ignoreSelectionUntil = now + 80;
        this.view.setSaved(false);
        this.view.setSourceDirty(false);
        this.view.renderAll(true, true);
        this.saveLocal();
        this.emitChange();
        return true;
    };

    EditorController.prototype.undo = function () {
        let snapshot;
        if (!this.model.undoStack.length) return;
        this.view.captureSelection();
        snapshot = this.model.undoStack.pop();
        this.model.redoStack.push(this.model.snapshot());
        this.model.restore(snapshot);
        this.lastGroup = "";
        this.ignoreSelectionUntil = new Date().getTime() + 80;
        this.view.renderAll(true, true);
        this.saveLocal();
        this.emitChange();
    };

    EditorController.prototype.redo = function () {
        let snapshot;
        if (!this.model.redoStack.length) return;
        this.view.captureSelection();
        snapshot = this.model.redoStack.pop();
        this.model.undoStack.push(this.model.snapshot());
        this.model.restore(snapshot);
        this.lastGroup = "";
        this.ignoreSelectionUntil = new Date().getTime() + 80;
        this.view.renderAll(true, true);
        this.saveLocal();
        this.emitChange();
    };

    EditorController.prototype.switchMode = function (mode) {
        if (mode !== "edit" && mode !== "preview" && mode !== "source") return;
        if (this.model.state.mode === "edit") this.view.captureSelection();
        this.model.state.mode = mode;
        if (mode === "source") this.view.setSourceDirty(false);
        this.view.renderMode();
        this.view.renderToolbar();
        if (mode === "edit") {
            this.ignoreSelectionUntil = new Date().getTime() + 80;
            this.view.restoreSelection(true);
        }
    };

    EditorController.prototype.openLinkDialog = function () {
        let selected;
        this.view.captureSelection();
        this.dialogKind = "link";
        this.dialogSelection = cloneValue(this.model.state.selection);
        selected = this.model.selectedText();
        this.view.openDialog({
            title: "插入链接",
            url: "https://",
            text: selected,
            showText: !selected,
            showAlt: false,
            confirmText: "插入"
        });
    };

    EditorController.prototype.openImageDialog = function () {
        this.view.captureSelection();
        this.dialogKind = "image";
        this.dialogSelection = cloneValue(this.model.state.selection);
        this.view.openDialog({
            title: "插入图片",
            url: "https://",
            showText: false,
            showAlt: true,
            confirmText: "插入"
        });
    };

    EditorController.prototype.openNewDialog = function () {
        this.dialogKind = "new";
        this.dialogSelection = cloneValue(this.model.state.selection);
        this.view.openDialog({
            title: "新建文档",
            showUrl: false,
            showText: false,
            showAlt: false,
            message: "当前文档会被替换，仍可使用撤销恢复。",
            confirmText: "新建"
        });
    };

    EditorController.prototype.confirmDialog = function () {
        let controller = this;
        let url = document.getElementById("dialogUrl").value;
        let text = document.getElementById("dialogText").value;
        let alt = document.getElementById("dialogAlt").value;
        let valid;
        if (this.dialogSelection) this.model.state.selection = cloneValue(this.dialogSelection);
        if (this.dialogKind === "link") {
            valid = safeUrl(url, false);
            if (!valid) {
                document.getElementById("dialogUrl").classList.add("is-invalid");
                return;
            }
            this.commit("link", function () {
                if (controller.model.isCollapsed()) {
                    if (!text) return false;
                    controller.model.state.storedMarks.link = valid;
                    controller.model.insertText(text);
                    controller.model.state.storedMarks.link = "";
                    return true;
                }
                return controller.model.applyMark("link", valid);
            }, "", true);
        } else if (this.dialogKind === "image") {
            valid = safeUrl(url, true);
            if (!valid) {
                document.getElementById("dialogUrl").classList.add("is-invalid");
                return;
            }
            this.commit("image", function () {
                return controller.model.insertAtomic("image", valid, alt);
            }, "", true);
        } else if (this.dialogKind === "new") {
            this.commit("new-document", function () {
                let mode = controller.model.state.mode;
                controller.model.state = controller.model.createBlankState();
                controller.model.state.mode = mode;
                return true;
            }, "", true);
        }
        this.closeDialog();
    };

    EditorController.prototype.closeDialog = function () {
        this.view.closeDialog();
        document.getElementById("dialogUrl").classList.remove("is-invalid");
        this.dialogKind = "";
        if (this.model.state.mode === "edit") {
            this.ignoreSelectionUntil = new Date().getTime() + 80;
            this.view.restoreSelection(true);
        }
    };

    EditorController.prototype.copyHtml = function () {
        let html = standaloneHtml(this.model);
        let controller = this;
        if (window.navigator.clipboard && window.navigator.clipboard.writeText) {
            window.navigator.clipboard.writeText(html).then(function () {
                controller.view.setSaved(true, "HTML 已复制");
            }, function () {
                controller.switchMode("source");
                controller.view.source.select();
                controller.view.setSaved(false, "请在源码视图复制");
            });
        } else {
            this.switchMode("source");
            this.view.source.select();
            this.view.setSaved(false, "请在源码视图复制");
        }
    };

    EditorController.prototype.downloadHtml = function () {
        let blob;
        let url;
        let anchor;
        let filename = trimText(this.model.state.title).replace(/[\\/:*?"<>|]+/g, "-") || "document";
        try {
            blob = new Blob([standaloneHtml(this.model)], { type: "text/html;charset=utf-8" });
            url = window.URL.createObjectURL(blob);
            anchor = document.createElement("a");
            anchor.href = url;
            anchor.download = filename + ".html";
            document.body.appendChild(anchor);
            anchor.click();
            document.body.removeChild(anchor);
            window.setTimeout(function () {
                window.URL.revokeObjectURL(url);
            }, 0);
            this.view.setSaved(true, "HTML 已导出");
        } catch (error) {
            this.view.setSaved(false, "当前环境无法下载");
        }
    };

    EditorController.prototype.handleToolbarAction = function (action) {
        let controller = this;
        if (action === "undo") this.undo();
        else if (action === "redo") this.redo();
        else if (action === "link") this.openLinkDialog();
        else if (action === "unlink") this.commit("unlink", function () { return controller.model.removeLink(); });
        else if (action === "image") this.openImageDialog();
        else if (action === "divider") this.commit("divider", function () { return controller.model.insertAtomic("divider"); });
        else if (action === "clear-format") this.commit("clear-format", function () { return controller.model.clearFormatting(); });
        else if (action === "indent") this.commit("indent", function () { return controller.model.changeIndent(1); });
        else if (action === "outdent") this.commit("outdent", function () { return controller.model.changeIndent(-1); });
        else if (action === "new-document") this.openNewDialog();
        else if (action === "copy-html") this.copyHtml();
        else if (action === "download-html") this.downloadHtml();
    };

    EditorController.prototype.copySelection = function (event, cut) {
        let blocks;
        let text;
        let html;
        let controller = this;
        if (!this.view.captureSelection() || this.model.isCollapsed()) return;
        blocks = this.model.selectedBlocks();
        text = this.model.selectedText();
        html = blocksToHtml(blocks);
        if (event.clipboardData && event.clipboardData.setData) {
            event.preventDefault();
            event.clipboardData.setData("text/plain", text);
            event.clipboardData.setData("text/html", html);
        }
        if (cut) this.commit("cut", function () { return controller.model.deleteSelection(); }, "", true);
    };

    EditorController.prototype.paste = function (event) {
        let html = "";
        let text = "";
        let blocks;
        let controller = this;
        if (!event.clipboardData) return;
        event.preventDefault();
        this.view.captureSelection();
        html = event.clipboardData.getData("text/html") || "";
        text = event.clipboardData.getData("text/plain") || "";
        if (html) {
            blocks = parseHtmlBlocks(html);
            if (blocks.length) {
                this.commit("paste-html", function () { return controller.model.insertFragment(blocks); }, "", true);
                return;
            }
        }
        this.commit("paste-text", function () { return controller.model.insertPlainText(text); }, "", true);
    };

    EditorController.prototype.handleBeforeInput = function (event) {
        let type = event.inputType || "";
        let data = event.data;
        let controller = this;
        if (this.composing || event.isComposing) return;
        event.preventDefault();
        if (type === "insertText" || type === "insertReplacementText") {
            this.commit("typing", function () { return controller.model.insertText(data || ""); }, "typing");
        } else if (type === "insertParagraph") {
            this.commit("paragraph", function () { return controller.model.insertParagraph(false); });
        } else if (type === "insertLineBreak") {
            this.commit("line-break", function () { return controller.model.insertParagraph(true); });
        } else if (type === "deleteContentBackward" || type === "deleteWordBackward" || type === "deleteSoftLineBackward") {
            this.commit("delete-backward", function () { return controller.model.deleteBackward(); }, "delete-backward");
        } else if (type === "deleteContentForward" || type === "deleteWordForward" || type === "deleteSoftLineForward") {
            this.commit("delete-forward", function () { return controller.model.deleteForward(); }, "delete-forward");
        } else if (type === "deleteByCut") {
            this.commit("cut", function () { return controller.model.deleteSelection(); });
        } else if (type === "historyUndo") {
            this.undo();
        } else if (type === "historyRedo") {
            this.redo();
        } else if (type === "formatBold") {
            this.commit("bold", function () { return controller.model.applyMark("bold"); });
        } else if (type === "formatItalic") {
            this.commit("italic", function () { return controller.model.applyMark("italic"); });
        } else if (type === "formatUnderline") {
            this.commit("underline", function () { return controller.model.applyMark("underline"); });
        }
    };

    EditorController.prototype.handleKeydown = function (event) {
        let control = event.ctrlKey || event.metaKey;
        let key = String(event.key || "").toLowerCase();
        let controller = this;
        let range;
        if (this.composing || event.isComposing) return;
        if (control && key === "z" && !event.shiftKey) {
            event.preventDefault();
            this.undo();
        } else if ((control && key === "y") || (control && event.shiftKey && key === "z")) {
            event.preventDefault();
            this.redo();
        } else if (control && key === "b") {
            event.preventDefault();
            this.commit("bold", function () { return controller.model.applyMark("bold"); });
        } else if (control && key === "i") {
            event.preventDefault();
            this.commit("italic", function () { return controller.model.applyMark("italic"); });
        } else if (control && key === "u") {
            event.preventDefault();
            this.commit("underline", function () { return controller.model.applyMark("underline"); });
        } else if (control && event.shiftKey && key === "x") {
            event.preventDefault();
            this.commit("strike", function () { return controller.model.applyMark("strike"); });
        } else if (control && key === "k") {
            event.preventDefault();
            this.openLinkDialog();
        } else if (control && key === "a") {
            event.preventDefault();
            this.model.selectAll();
            this.ignoreSelectionUntil = new Date().getTime() + 80;
            this.view.renderAll(true, true);
        } else if (key === "enter") {
            event.preventDefault();
            this.commit(event.shiftKey ? "line-break" : "paragraph", function () {
                return controller.model.insertParagraph(event.shiftKey);
            });
        } else if (key === "backspace") {
            event.preventDefault();
            this.commit("delete-backward", function () { return controller.model.deleteBackward(); }, "delete-backward");
        } else if (key === "delete") {
            event.preventDefault();
            this.commit("delete-forward", function () { return controller.model.deleteForward(); }, "delete-forward");
        } else if (key === "tab") {
            event.preventDefault();
            range = this.model.range();
            if (this.model.state.blocks[range.start.block].type === "code") {
                this.commit("typing", function () { return controller.model.insertText("    "); }, "typing");
            } else {
                this.commit(event.shiftKey ? "outdent" : "indent", function () {
                    return controller.model.changeIndent(event.shiftKey ? -1 : 1);
                });
            }
        }
    };

    EditorController.prototype.applySource = function () {
        let blocks = parseHtmlBlocks(this.view.source.value);
        let controller = this;
        if (!blocks.length) {
            this.view.source.classList.add("is-invalid");
            this.view.sourceState.textContent = "没有可导入内容";
            this.view.sourceState.className = "text-danger";
            return;
        }
        this.view.source.classList.remove("is-invalid");
        this.commit("source", function () {
            controller.model.state.blocks = blocks;
            controller.model.collapse({ block: 0, offset: 0 });
            return true;
        }, "", true);
        this.view.setSourceDirty(false);
        this.switchMode("edit");
    };

    EditorController.prototype.bind = function () {
        let controller = this;
        let toolbarRoot = document.querySelector(".editor-app");
        let dialogBackdrop = document.getElementById("editorDialogBackdrop");

        toolbarRoot.addEventListener("mousedown", function (event) {
            let target = event.target;
            while (target && target !== toolbarRoot) {
                if (target.nodeName === "BUTTON" && (target.hasAttribute("data-action") || target.hasAttribute("data-mark")
                    || target.hasAttribute("data-block-action") || target.hasAttribute("data-align"))) {
                    event.preventDefault();
                    return;
                }
                target = target.parentNode;
            }
        });

        toolbarRoot.addEventListener("click", function (event) {
            let target = event.target;
            let action;
            let mark;
            let blockAction;
            let alignment;
            while (target && target !== toolbarRoot && target.nodeType === 1) {
                action = target.getAttribute("data-action");
                mark = target.getAttribute("data-mark");
                blockAction = target.getAttribute("data-block-action");
                alignment = target.getAttribute("data-align");
                if (action || mark || blockAction || alignment) break;
                target = target.parentNode;
            }
            if (!target || target === toolbarRoot) return;
            if (controller.model.state.mode === "edit") controller.view.captureSelection();
            if (action) controller.handleToolbarAction(action);
            else if (mark) controller.commit(mark, function () { return controller.model.applyMark(mark); }, "", true);
            else if (blockAction) controller.commit("list", function () { return controller.model.setBlockType(blockAction); }, "", true);
            else if (alignment) controller.commit("align", function () { return controller.model.setAlignment(alignment); }, "", true);
        });

        document.getElementById("blockType").addEventListener("change", function () {
            let type = this.value;
            controller.commit("block-type", function () { return controller.model.setBlockType(type); });
        });

        document.getElementById("textColor").addEventListener("change", function () {
            let color = this.value;
            controller.commit("text-color", function () { return controller.model.applyMark("color", color); });
        });

        document.getElementById("highlightColor").addEventListener("change", function () {
            let color = this.value;
            controller.commit("highlight", function () { return controller.model.applyMark("background", color); });
        });

        document.querySelector(".editor-view-tabs").addEventListener("click", function (event) {
            let target = event.target;
            if (target && target.hasAttribute("data-mode")) controller.switchMode(target.getAttribute("data-mode"));
        });

        this.view.surface.addEventListener("beforeinput", function (event) {
            controller.handleBeforeInput(event);
        });
        this.view.surface.addEventListener("keydown", function (event) {
            controller.handleKeydown(event);
        });
        this.view.surface.addEventListener("copy", function (event) {
            controller.copySelection(event, false);
        });
        this.view.surface.addEventListener("cut", function (event) {
            controller.copySelection(event, true);
        });
        this.view.surface.addEventListener("paste", function (event) {
            controller.paste(event);
        });
        this.view.surface.addEventListener("drop", function (event) {
            let html;
            let text;
            let blocks;
            event.preventDefault();
            if (!event.dataTransfer) return;
            html = event.dataTransfer.getData("text/html") || "";
            text = event.dataTransfer.getData("text/plain") || "";
            blocks = html ? parseHtmlBlocks(html) : [];
            if (blocks.length) controller.commit("drop-html", function () { return controller.model.insertFragment(blocks); });
            else controller.commit("drop-text", function () { return controller.model.insertPlainText(text); });
        });
        this.view.surface.addEventListener("mousedown", function (event) {
            let target = event.target;
            let blockIndex;
            while (target && target !== controller.view.surface) {
                if (target.nodeType === 1 && target.hasAttribute("data-atomic")) {
                    event.preventDefault();
                    blockIndex = parseInt(target.getAttribute("data-block-index"), 10);
                    controller.model.setSelection({ block: blockIndex, offset: 0 }, { block: blockIndex, offset: 1 }, false);
                    controller.ignoreSelectionUntil = new Date().getTime() + 80;
                    controller.view.renderAll(true, true);
                    return;
                }
                target = target.parentNode;
            }
        });
        this.view.surface.addEventListener("compositionstart", function () {
            controller.view.captureSelection();
            controller.compositionSelection = cloneValue(controller.model.state.selection);
            controller.composing = true;
        });
        this.view.surface.addEventListener("compositionend", function (event) {
            let text = event.data || "";
            controller.composing = false;
            if (controller.compositionSelection) controller.model.state.selection = cloneValue(controller.compositionSelection);
            controller.commit("composition", function () { return controller.model.insertText(text); }, "typing", true);
            controller.compositionSelection = null;
        });
        this.view.surface.addEventListener("input", function () {
            if (!controller.composing) {
                controller.ignoreSelectionUntil = new Date().getTime() + 80;
                controller.view.renderAll(true, true);
            }
        });

        document.addEventListener("selectionchange", function () {
            if (controller.composing || controller.view.rendering || controller.model.state.mode !== "edit") return;
            if (new Date().getTime() < controller.ignoreSelectionUntil) return;
            if (controller.view.captureSelection()) {
                controller.view.renderToolbar();
                controller.view.renderStats();
            }
        });

        document.getElementById("documentTitle").addEventListener("input", function () {
            controller.model.state.title = this.value;
            controller.view.setSaved(false);
            controller.saveLocal();
        });

        document.getElementById("documentOutline").addEventListener("click", function (event) {
            let target = event.target;
            let index;
            if (!target || !target.hasAttribute("data-outline-index")) return;
            index = parseInt(target.getAttribute("data-outline-index"), 10);
            controller.model.collapse({ block: index, offset: 0 });
            controller.switchMode("edit");
            controller.ignoreSelectionUntil = new Date().getTime() + 80;
            controller.view.renderAll(true, true);
        });

        this.view.source.addEventListener("input", function () {
            controller.view.setSourceDirty(true);
        });
        this.view.source.addEventListener("keydown", function (event) {
            if ((event.ctrlKey || event.metaKey) && String(event.key || "").toLowerCase() === "s") {
                event.preventDefault();
                controller.applySource();
            }
        });
        document.getElementById("applySourceButton").addEventListener("click", function () {
            controller.applySource();
        });

        dialogBackdrop.addEventListener("click", function (event) {
            let target = event.target;
            let action;
            if (target === dialogBackdrop) {
                controller.closeDialog();
                return;
            }
            while (target && target !== dialogBackdrop) {
                action = target.getAttribute ? target.getAttribute("data-dialog-action") : "";
                if (action) break;
                target = target.parentNode;
            }
            if (action === "confirm") controller.confirmDialog();
            if (action === "cancel") controller.closeDialog();
        });
        dialogBackdrop.addEventListener("keydown", function (event) {
            if (String(event.key || "").toLowerCase() === "escape") controller.closeDialog();
            if (String(event.key || "").toLowerCase() === "enter" && event.target.nodeName !== "TEXTAREA") {
                event.preventDefault();
                controller.confirmDialog();
            }
        });
    };

    let model = new EditorModel();
    let view = new EditorView(model);
    let controller = new EditorController(model, view);
    controller.loadLocal();
    model.normalize();
    controller.bind();
    view.renderAll(true, false);
    controller.saveLocal();
}());
