(function () {
    var vueH = Vue.h;
    var Mc = McUIVue;
    var components = {
        "mc-icon": Mc.McIcon, "mc-button": Mc.McButton, "mc-card": Mc.McCard,
        "mc-panel": Mc.McPanel, "mc-tooltip": Mc.McTooltip, "mc-progress": Mc.McProgress,
        "mc-spinner": Mc.McSpinner, "mc-checkbox": Mc.McCheckbox, "mc-radio": Mc.McRadio,
        "mc-radio-group": Mc.McRadioGroup, "mc-form-field": Mc.McFormField, "mc-switch": Mc.McSwitch,
        "mc-dropdown": Mc.McDropdown, "mc-text-field": Mc.McTextField, "mc-slider": Mc.McSlider,
        "mc-layout": Mc.McLayout, "mc-header": Mc.McHeader, "mc-appbar": Mc.McAppbar,
        "mc-appbar-button": Mc.McAppbarButton, "mc-appbar-icon": Mc.McAppbarIcon, "mc-tabs": Mc.McTabs,
        "mc-button-tabs": Mc.McButtonTabs, "mc-list": Mc.McList, "mc-list-item": Mc.McListItem,
        "mc-scroll-view": Mc.McScrollView, "mc-modal": Mc.McModal, "mc-confirm": Mc.McConfirm,
        "mc-drawer": Mc.McDrawer, "mc-loading-mask": Mc.McLoadingMask, "mc-pop-host": Mc.McPopHost,
        "mc-tcode": Mc.McTcode,
        "mc-formatted-text": Mc.McFormattedText
    };
    function h(type, props, children) {
        return vueH(components[type] || type, props, children);
    }

    var state = Vue.reactive({
        status: "32 个元素已挂载，可逐项检查。",
        checkbox: false,
        radio: "a",
        radioGroup: "survival",
        switchValue: true,
        dropdown: 0,
        text: "",
        progress: 60,
        slider: 50,
        tab: "tab1",
        buttonTab: "a",
        layoutDrawer: false,
        modal: false,
        confirm: false,
        drawer: false,
        loading: false
    });

    // Keep high-frequency demo state inside child render effects.  The
    // overview contains hundreds of nodes; rebuilding its root for every
    // pointer pixel or typed character makes it behave unlike the isolated
    // detail demos.
    var StatefulDemoContent = {
        render: function () {
            return this.$slots.default ? this.$slots.default() : null;
        }
    };
    var StatusView = {
        render: function () {
            return h("div", { class: "ore-overview-status", "aria-live": "polite" }, state.status);
        }
    };

    var dropdownOptions = ["选项一", "选项二", "选项三"];
    var radioOptions = [
        { label: "生存模式", value: "survival" },
        { label: "创造模式", value: "creative" }
    ];
    var tabItems = [
        { label: "标签一", value: "tab1" },
        { label: "标签二", value: "tab2" },
        { label: "标签三(禁用)", value: "tab3", disabled: true }
    ];
    var buttonTabItems = [
        { label: "选项A", value: "a", bgcolor: "#9a3f3f" },
        { label: "选项B", value: "b" },
        { label: "选项C(禁用)", value: "c", disabled: true }
    ];

    function setStatus(message) {
        state.status = message;
    }

    function update(key, value) {
        state[key] = value;
        setStatus(key + " = " + String(value));
    }

    function card(title, description, target) {
        return h("mc-card", {
            text: title,
            description: description,
            href: target,
            role: "link",
            tabindex: 0,
            onClick: function (event) {
                if (event && event.preventDefault) event.preventDefault();
                setStatus("card = " + target + " via pointer");
            },
            onKeydown: function (event) {
                if (!event || (event.key !== "Enter" && event.key !== " ")) return;
                if (event.preventDefault) event.preventDefault();
                setStatus("card = " + target + " via keyboard");
            }
        });
    }

    function button(label, variant, onClick, extra) {
        var props = { variant: variant || "normal", onClick: onClick };
        if (extra) Object.keys(extra).forEach(function (key) { props[key] = extra[key]; });
        return h("mc-button", props, { default: function () { return label; } });
    }

    function demo(name, label, children, className) {
        var containerClass = className === "mc-layout-demo"
            ? className
            : "mc-demo" + (className ? " " + className : "");
        return h("section", { class: "ore-component-case", "data-component": name, id: name }, [
            h("h3", null, h("a", {
                class: "ore-component-link",
                href: "#" + name
            }, label)),
            h("div", { class: containerClass }, children)
        ]);
    }

    function statefulDemo(name, label, childrenFactory, className) {
        return demo(name, label, [
            h(StatefulDemoContent, null, { default: childrenFactory })
        ], className);
    }

    function group(title, children) {
        return h("section", { class: "ore-component-group" }, [h("h2", null, title)].concat(children));
    }

    function appbar(left, right, props) {
        return h("mc-appbar", props || null, {
            left: function () { return left || []; },
            right: function () { return right || []; }
        });
    }

    function listItems() {
        return [
            h("mc-list-item", { label: "世界一", subtitle: "生存模式", value: "world-1", icon: "mc-world" }),
            h("mc-list-item", { label: "世界二", subtitle: "创造模式", value: "world-2", icon: "mc-home" }),
            h("mc-list-item", { label: "世界三", subtitle: "冒险模式", value: "world-3" }),
            h("mc-list-item", { label: "世界四", value: "world-4", disabled: true })
        ];
    }

    var Root = {
        render: function () {
            var basic = [
                demo("mc-icon", "Icon 图标", [
                    h("mc-icon", { name: "mc-clear", size: "24" }),
                    h("mc-icon", { name: "mc-check-white", size: "24" }),
                    h("mc-icon", { name: "mc-chevron-right", size: "24" }),
                    h("mc-icon", { name: "mc-chevron-left", size: "24" }),
                    h("mc-icon", { name: "mc-magnifying-glass", size: "24" }),
                    h("mc-icon", { name: "mc-settings", size: "24" }),
                    h("mc-icon", { name: "mc-home", size: "24" }),
                    h("mc-icon", { name: "mc-clipboard", size: "24" })
                ]),
                demo("mc-button", "Button 按钮", [
                    button("主按钮", "primary", function () { setStatus("主按钮"); }),
                    button("默认按钮", "normal", function () { setStatus("默认按钮"); }),
                    button("危险按钮", "error", function () { setStatus("危险按钮"); }),
                    button("自定义", "normal", function () { setStatus("自定义按钮"); }, { bgcolor: "#ff6b35" }),
                    button("禁用按钮", "normal", null, { disabled: true })
                ]),
                demo("mc-card", "Card 链接卡片", [
                    card("关于我们", "了解更多信息", "#about"),
                    card("帮助中心", "获取帮助", "#help")
                ]),
                demo("mc-panel", "Panel 面板", [
                    h("mc-panel", { title: "面板标题" }, { default: function () { return "面板内容"; } })
                ]),
                demo("mc-tooltip", "Tooltip 提示", [
                    h("mc-tooltip", { content: "这是一个提示" }, { default: function () { return button("悬停查看", "normal", function () { setStatus("Tooltip 按钮"); }); } })
                ])
            ];

            var forms = [
                demo("mc-checkbox", "Checkbox 复选框", [
                    h("mc-checkbox", { modelValue: state.checkbox, "onUpdate:modelValue": function (value) { update("checkbox", value); } }, { default: function () { return "开启音效"; } }),
                    h("mc-checkbox", { modelValue: true }, { default: function () { return "已选中"; } }),
                    h("mc-checkbox", { modelValue: true, disabled: true }, { default: function () { return "已禁用"; } })
                ]),
                demo("mc-radio", "Radio 单选", [
                    h("mc-radio", { modelValue: state.radio, value: "a", "onUpdate:modelValue": function (value) { update("radio", value); } }, { default: function () { return "选项 A"; } }),
                    h("mc-radio", { modelValue: state.radio, value: "b", "onUpdate:modelValue": function (value) { update("radio", value); } }, { default: function () { return "选项 B"; } }),
                    h("mc-radio", { modelValue: "b", value: "b", disabled: true }, { default: function () { return "已禁用"; } })
                ]),
                demo("mc-radio-group", "RadioGroup 单选组", [
                    h("mc-radio-group", { modelValue: state.radioGroup, options: radioOptions, "onUpdate:modelValue": function (value) { update("radioGroup", value); } })
                ]),
                statefulDemo("mc-form-field", "FormField 表单项", function () { return [
                    h("mc-form-field", { label: "用户名", description: "请输入您的用户名。" }, { default: function () { return h("mc-text-field", { modelValue: state.text, placeholder: "Steve", "onUpdate:modelValue": function (value) { update("text", value); } }); } })
                ]; }, "mc-demo--column mc-demo--narrow-320"),
                demo("mc-switch", "Switch 开关", [
                    h("mc-switch", { modelValue: state.switchValue, "onUpdate:modelValue": function (value) { update("switchValue", value); } }),
                    h("mc-switch", { modelValue: false }),
                    h("mc-switch", { modelValue: true, disabled: true })
                ]),
                demo("mc-dropdown", "Dropdown 下拉选择", [
                    h("mc-dropdown", { options: dropdownOptions, modelValue: state.dropdown, unselectedText: "请选择", "onUpdate:modelValue": function (value) { update("dropdown", value); } }),
                    h("mc-dropdown", { options: dropdownOptions, modelValue: 0, disabled: true, unselectedText: "已禁用" })
                ]),
                statefulDemo("mc-text-field", "TextField 文本框", function () { return [
                    h("mc-text-field", { modelValue: state.text, placeholder: "请输入内容", "onUpdate:modelValue": function (value) { update("text", value); } }),
                    h("mc-text-field", { placeholder: "已禁用", disabled: true })
                ]; }),
                statefulDemo("mc-progress", "Progress 进度条", function () { return [
                    h("mc-progress", { value: state.progress, label: "进度值", max: 100 }),
                    h("mc-slider", { modelValue: state.progress, min: 0, max: 100, step: 1, "onUpdate:modelValue": function (value) { update("progress", value); } })
                ]; }, "mc-demo--column mc-demo--narrow-300"),
                demo("mc-spinner", "Spinner 加载动画", [
                    h("mc-spinner", { size: 48 }), h("mc-spinner", { size: 64 })
                ]),
                statefulDemo("mc-slider", "Slider 滑动条", function () { return [
                    h("mc-slider", { modelValue: state.slider, min: 0, max: 100, step: 1, tip: true, "onUpdate:modelValue": function (value) { state.slider = value; } }),
                    h("mc-slider", { modelValue: 50, disabled: true })
                ]; }, "mc-demo--column mc-demo--narrow-300")
            ];

            var layout = [
                demo("mc-layout", "Layout", [
                    h("mc-layout", null, { default: function () { return [
                        appbar([
                            h("mc-appbar-icon", { icon: "mc-menu", tip: "抽屉栏", onClick: function () { state.layoutDrawer = true; } }),
                            h("mc-appbar-icon", { icon: "mc-chevron-left", tip: "返回" })
                        ], [
                            h("mc-appbar-button", { icon: "mc-home" }, { default: function () { return "制作"; } }),
                            h("mc-appbar-button", { icon: "mc-world" }, { default: function () { return "世界"; } }),
                            h("mc-appbar-button", { icon: "mc-friends" }, { default: function () { return "社交"; } })
                        ]),
                        h("mc-scroll-view", null, { default: function () { return h("div", { class: "mc-layout-demo__content" }, [
                            h("mc-panel", { title: "存档列表", subtitle: "Layout 会提供顶部标题栏与可滚动主体区域" }, { default: function () { return [
                                h("p", null, "这里是页面内容区域，可放置任意 McUI 组件。"),
                                h("p", null, "文档站中用固定高度容器模拟全屏页面，实际项目可直接作为页面根布局使用。")
                            ]; } }),
                            button("进入世界", "primary", function () { setStatus("进入世界"); })
                        ]); } })
                    ]; } }),
                    h("mc-drawer", { open: state.layoutDrawer, title: "导航菜单", placement: "left", teleport: false, "onUpdate:open": function (value) { state.layoutDrawer = value; } }, { default: function () { return h("mc-list", { onChange: function () { state.layoutDrawer = false; } }, { default: function () { return [
                        h("mc-list-item", { label: "首页", value: "home", icon: "mc-home" }),
                        h("mc-list-item", { label: "服务器列表", value: "servers", icon: "mc-world" }),
                        h("mc-list-item", { label: "玩家中心", value: "players", icon: "mc-friends" }),
                        h("mc-list-item", { label: "设置", value: "settings", icon: "mc-settings" })
                    ]; } }); } })
                ], "mc-layout-demo"),
                demo("mc-header", "Header 标题栏", [h("mc-header", { title: "组件标题" })], "mc-demo--flush"),
                demo("mc-appbar", "Appbar 顶栏", [appbar([
                    h("mc-appbar-icon", { icon: "mc-chevron-left", tip: "返回" })
                ], [
                    h("mc-appbar-button", { icon: "mc-home" }, { default: function () { return "制作"; } }),
                    h("mc-appbar-button", { icon: "mc-world" }, { default: function () { return "世界"; } })
                ])], "mc-demo--column mc-demo--flush"),
                demo("mc-appbar-button", "AppbarButton 顶栏按钮", [appbar([], [
                    h("mc-appbar-button", { icon: "mc-home", onClick: function () { setStatus("AppbarButton"); } }, { default: function () { return "制作"; } }),
                    h("mc-appbar-button", { icon: "mc-world" }, { default: function () { return "世界"; } }),
                    h("mc-appbar-button", { icon: "mc-friends" }, { default: function () { return "社交"; } })
                ])]),
                demo("mc-appbar-icon", "AppbarIcon 图标按钮", [appbar([
                    h("mc-appbar-icon", { icon: "mc-chevron-left", onClick: function () { setStatus("AppbarIcon"); } }),
                    h("mc-appbar-icon", { icon: "mc-menu" })
                ], [], { title: "编辑器" })]),
                demo("mc-tabs", "Tabs 标签页", [
                    h("mc-tabs", { modelValue: state.tab, items: tabItems, "onUpdate:modelValue": function (value) { update("tab", value); } }, { default: function () { return h("div", null, state.tab === "tab2" ? "标签二的内容区域" : "标签一的内容区域"); } })
                ], "mc-demo--column"),
                demo("mc-button-tabs", "ButtonTabs 按钮式标签", [
                    h("mc-button-tabs", { modelValue: state.buttonTab, items: buttonTabItems, "onUpdate:modelValue": function (value) { update("buttonTab", value); } })
                ], "mc-demo--column"),
                demo("mc-list", "List 列表", [
                    h("mc-list", { onChange: function (value) { setStatus("list = " + String(value)); } }, { default: function () { return [
                        h("mc-list-item", { label: "列表项一", value: "a", subtitle: "副标题" }),
                        h("mc-list-item", { label: "列表项二", value: "b", icon: "mc-plus" }),
                        h("mc-list-item", { label: "列表项三", value: "c" }),
                        h("mc-list-item", { label: "列表项四", value: "d", disabled: true })
                    ]; } })
                ]),
                demo("mc-list-item", "ListItem 列表项", [
                    h("mc-list", { modelValue: "single", mode: "single" }, { default: function () { return [h("mc-list-item", { label: "独立列表项", subtitle: "可点击检查状态", value: "single", icon: "mc-home" })]; } })
                ]),
                demo("mc-scroll-view", "ScrollView 滚动区", [
                    h("mc-scroll-view", { style: { width: "240px", height: "100px" } }, { default: function () { return h("mc-list", null, { default: function () { return [
                        h("mc-list-item", { label: "世界一 · 生存模式", value: "w1" }),
                        h("mc-list-item", { label: "世界二 · 创造模式", value: "w2" }),
                        h("mc-list-item", { label: "世界三 · 冒险模式", value: "w3" }),
                        h("mc-list-item", { label: "世界四 · 服务器", value: "w4" }),
                        h("mc-list-item", { label: "世界五 · 测试存档", value: "w5" })
                    ]; } }); } })
                ])
            ];

            var feedback = [
                demo("mc-modal", "Modal 弹窗", [
                    button("打开弹窗", "primary", function () { state.modal = true; }),
                    h("mc-modal", { open: state.modal, title: "弹窗标题", "onUpdate:open": function (value) { state.modal = value; } }, { default: function () { return "这是弹窗内容。"; } })
                ]),
                demo("mc-confirm", "Confirm 确认弹窗", [
                    button("危险操作", "error", function () { state.confirm = true; }),
                    h("mc-confirm", { open: state.confirm, title: "确认删除？", "onUpdate:open": function (value) { state.confirm = value; }, onConfirm: function () { state.confirm = false; setStatus("已确认删除"); } }, { default: function () { return "此操作不可撤销。"; } })
                ]),
                demo("mc-drawer", "Drawer 抽屉", [
                    button("打开抽屉", "normal", function () { state.drawer = true; }),
                    h("mc-drawer", { open: state.drawer, title: "抽屉标题", "onUpdate:open": function (value) { state.drawer = value; } }, { default: function () { return "抽屉内容区域。"; } })
                ]),
                demo("mc-pop-host", "Pop 提示", [
                    h("mc-pop-host"),
                    button("成功提示", "normal", function () { if (Mc.showPop) Mc.showPop("已保存世界", 2000, "success"); setStatus("成功提示"); }),
                    button("错误提示", "normal", function () { if (Mc.showPop) Mc.showPop("保存失败", 2000, "error"); setStatus("错误提示"); })
                ]),
                demo("mc-loading-mask", "LoadingMask 加载遮罩", [
                    button("显示加载遮罩（1.8s）", "normal", function () { state.loading = true; setTimeout(function () { state.loading = false; }, 1800); }),
                    h("mc-loading-mask", { visible: state.loading, text: "生成世界中" })
                ])
            ];

            var content = [
                demo("mc-tcode", "TCode 格式代码", [
                    h("mc-tcode", { text: "§bTCode §l粗体 §r输出" })
                ]),
                demo("mc-formatted-text", "FormattedText 格式化文本", [
                    h("mc-formatted-text", { text: "§a绿色 §l粗体 §r普通文本" })
                ])
            ];

            var groups = [
                { title: "基础", demos: basic },
                { title: "表单", demos: forms },
                { title: "布局", demos: layout },
                { title: "反馈", demos: feedback },
                { title: "内容", demos: content }
            ];
            var overviewGroups = groups.map(function (entry) {
                return group(entry.title, entry.demos);
            });

            return h("div", { class: "ore-theme ore-overview-shell" }, [
                h(StatusView),
                h("main", { class: "ore-overview-scroll" }, [
                    h("div", { class: "ore-overview-content" }, [
                        h("h1", null, "组件总览"),
                        h("p", { class: "ore-overview-lead" }, "McUI Vue 保留的 32 个元素集中展示；标题链接仅在本页定位。"),
                        overviewGroups[0],
                        overviewGroups[1],
                        overviewGroups[2],
                        overviewGroups[3],
                        overviewGroups[4]
                    ])
                ])
            ]);
        }
    };

    var root = document.getElementById("showcase-root");
    var app = Vue.createApp(Root);
    app.use(Mc.default);
    app.mount(root);
    document.body.setAttribute("data-mcui-components", "32");
})();
