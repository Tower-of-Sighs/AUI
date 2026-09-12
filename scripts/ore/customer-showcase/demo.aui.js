(function () {
    "use strict";

    var Mc = McUIVue;
    var components = {
        "mc-icon": Mc.McIcon,
        "mc-button": Mc.McButton,
        "mc-card": Mc.McCard,
        "mc-panel": Mc.McPanel,
        "mc-tooltip": Mc.McTooltip,
        "mc-progress": Mc.McProgress,
        "mc-spinner": Mc.McSpinner,
        "mc-checkbox": Mc.McCheckbox,
        "mc-radio": Mc.McRadio,
        "mc-radio-group": Mc.McRadioGroup,
        "mc-form-field": Mc.McFormField,
        "mc-switch": Mc.McSwitch,
        "mc-dropdown": Mc.McDropdown,
        "mc-text-field": Mc.McTextField,
        "mc-slider": Mc.McSlider,
        "mc-layout": Mc.McLayout,
        "mc-header": Mc.McHeader,
        "mc-appbar": Mc.McAppbar,
        "mc-appbar-button": Mc.McAppbarButton,
        "mc-appbar-icon": Mc.McAppbarIcon,
        "mc-tabs": Mc.McTabs,
        "mc-button-tabs": Mc.McButtonTabs,
        "mc-list": Mc.McList,
        "mc-list-item": Mc.McListItem,
        "mc-scroll-view": Mc.McScrollView,
        "mc-modal": Mc.McModal,
        "mc-confirm": Mc.McConfirm,
        "mc-drawer": Mc.McDrawer,
        "mc-loading-mask": Mc.McLoadingMask,
        "mc-pop-host": Mc.McPopHost,
        "mc-tcode": Mc.McTcode,
        "mc-formatted-text": Mc.McFormattedText
    };
    var renderedComponents = {};

    var state = Vue.reactive({
        drawer: false,
        modal: false,
        confirm: false,
        loading: false,
        workspace: "overview",
        releaseChannel: "stable",
        selectedWorld: "aurora",
        worldName: "极光谷",
        seed: "AUI-2026-ORE",
        difficulty: 3,
        mode: "survival",
        preferredMode: "survival",
        sound: true,
        backup: true,
        defaultRelease: true,
        simulationDistance: 68,
        buildProgress: 72,
        statusCode: "§aREADY §7/ §bORE WORKSPACE",
        statusText: "§a工作台已就绪。§r 最近一次资源校验通过。"
    });

    var workspaceTabs = [
        { label: "工作台", value: "overview" },
        { label: "世界设置", value: "settings" },
        { label: "发布记录", value: "history" }
    ];
    var releaseTabs = [
        { label: "稳定通道", value: "stable" },
        { label: "预览通道", value: "preview" },
        { label: "内部通道", value: "internal" }
    ];
    var difficulties = ["和平", "简单", "普通", "困难"];
    var modes = [
        { label: "生存模式", value: "survival" },
        { label: "创造模式", value: "creative" },
        { label: "冒险模式", value: "adventure" }
    ];

    function mc(name, props, slots) {
        renderedComponents[name] = true;
        return Vue.h(components[name], props || null, slots);
    }

    function button(label, variant, action, props) {
        var options = { variant: variant || "normal", onClick: action };
        Object.keys(props || {}).forEach(function (key) { options[key] = props[key]; });
        return mc("mc-button", options, { default: function () { return label; } });
    }

    function setStatus(code, text) {
        state.statusCode = code;
        state.statusText = text;
    }

    function showPop(message, type) {
        if (Mc.showPop) Mc.showPop(message, 2200, type || "success");
    }

    function saveSettings() {
        setStatus("§aSAVED §7/ §f" + state.worldName, "§a世界设置已保存。§r 新配置将在下次进入世界时生效。");
        showPop("世界设置已保存", "success");
    }

    function finishPublish() {
        state.loading = false;
        state.buildProgress = 100;
        setStatus("§aPUBLISHED §7/ §b" + state.releaseChannel, "§a版本已发布。§r 客户预览与资源清单已同步。");
        showPop("版本发布完成", "success");
    }

    function beginPublish() {
        state.confirm = false;
        state.loading = true;
        state.buildProgress = 84;
        setStatus("§ePUBLISHING §7/ §fPLEASE WAIT", "§e正在生成发布包。§r 正在校验字体、图标、音效与交互资源。");
        setTimeout(finishPublish, 900);
    }

    function appbar() {
        return mc("mc-appbar", { title: "Ore 世界工作台" }, {
            left: function () {
                return [
                    mc("mc-appbar-icon", { id: "demo-open-drawer", "data-demo-action": "open-drawer", icon: "mc-menu", tip: "打开导航", onClick: function () { state.drawer = true; } }),
                    mc("mc-appbar-icon", { icon: "mc-chevron-left", tip: "返回", onClick: function () { setStatus("§7BACK", "§7当前已位于工作台首页。"); } })
                ];
            },
            right: function () {
                return [
                    mc("mc-appbar-button", { icon: "mc-home", onClick: function () { state.workspace = "overview"; } }, { default: function () { return "制作"; } }),
                    mc("mc-appbar-button", { icon: "mc-world", onClick: function () { state.workspace = "settings"; } }, { default: function () { return "世界"; } }),
                    mc("mc-appbar-button", { icon: "mc-friends", onClick: function () { state.modal = true; } }, { default: function () { return "协作"; } })
                ];
            }
        });
    }

    function metricCard(text, description, value, note, action) {
        return Vue.h("div", { class: "demo-metric-card" }, [
            mc("mc-card", {
                text: text,
                description: description,
                href: "#workspace",
                onClick: function (event) {
                    if (event && event.preventDefault) event.preventDefault();
                    action();
                }
            }, {
                default: function () {
                    return Vue.h("div", null, [
                        Vue.h("strong", { class: "demo-metric-number" }, value),
                        Vue.h("span", { class: "demo-metric-label" }, text),
                        Vue.h("span", { class: "demo-metric-note" }, note)
                    ]);
                }
            })
        ]);
    }

    function worldList() {
        return mc("mc-list", {
            modelValue: state.selectedWorld,
            mode: "single",
            onChange: function (value) {
                state.selectedWorld = value;
                setStatus("§bWORLD §7/ §f" + String(value).toUpperCase(), "§7已切换当前世界，右侧发布状态同步更新。");
            }
        }, {
            default: function () {
                return [
                    mc("mc-list-item", { label: "极光谷", subtitle: "生存 · 1.8 GB · 刚刚同步", value: "aurora", icon: "mc-world" }),
                    mc("mc-list-item", { label: "红石工坊", subtitle: "创造 · 640 MB · 12 分钟前", value: "redstone", icon: "mc-home" }),
                    mc("mc-list-item", { label: "远古遗迹", subtitle: "冒险 · 920 MB · 昨天", value: "ruins", icon: "mc-clipboard" }),
                    mc("mc-list-item", { label: "归档测试服", subtitle: "只读 · 已归档", value: "archive", disabled: true, icon: "mc-settings" })
                ];
            }
        });
    }

    function statusLog() {
        return Vue.h("div", { class: "demo-log", "data-demo-status": "true" }, [
            Vue.h("span", { class: "demo-log-label" }, "实时状态"),
            Vue.h("div", { class: "demo-log-line" }, [
                mc("mc-tcode", { text: state.statusCode }),
                mc("mc-formatted-text", { text: state.statusText })
            ])
        ]);
    }

    function overviewPanel() {
        return Vue.h("div", { class: "demo-grid" }, [
            Vue.h("div", { class: "demo-stack" }, [
                mc("mc-panel", { title: "当前世界", subtitle: "极光谷 · 客户演示环境" }, {
                    default: function () {
                        return Vue.h("div", { class: "demo-panel-body" }, [
                            Vue.h("div", { class: "demo-world-summary" }, [
                                Vue.h("div", null, [
                                    Vue.h("h2", null, state.worldName),
                                    Vue.h("p", null, "资源、交互与界面状态均来自本地 Ore 运行时。")
                                ]),
                                button("查看版本详情", "normal", function () { state.modal = true; }, { id: "demo-open-modal", "data-demo-action": "open-modal" })
                            ]),
                            Vue.h("div", { class: "demo-chip-row" }, [
                                Vue.h("span", { class: "demo-chip" }, "NeoForge 26.1"),
                                Vue.h("span", { class: "demo-chip" }, "纯 Java 运行时"),
                                Vue.h("span", { class: "demo-chip" }, "离线资源")
                            ]),
                            Vue.h("div", { class: "demo-progress-row" }, [
                                mc("mc-progress", { value: state.buildProgress, max: 100, label: "发布准备度" }),
                                Vue.h("div", { class: "demo-build-state" }, [
                                    mc("mc-spinner", { size: 34 }),
                                    Vue.h("span", null, state.buildProgress >= 100 ? "发布完成" : "资源已就绪")
                                ])
                            ]),
                            mc("mc-button-tabs", { class: "demo-button-tabs", modelValue: state.releaseChannel, items: releaseTabs, "onUpdate:modelValue": function (value) { state.releaseChannel = value; setStatus("§bCHANNEL §7/ §f" + value.toUpperCase(), "§7发布通道已切换。"); } }),
                            statusLog()
                        ]);
                    }
                }),
                mc("mc-panel", { title: "近期活动", subtitle: "可滚动的世界与构建事件" }, {
                    default: function () {
                        return Vue.h("div", { class: "demo-list-region" }, [
                            mc("mc-scroll-view", null, { default: worldList })
                        ]);
                    }
                })
            ]),
            Vue.h("div", { class: "demo-stack" }, [
                mc("mc-panel", { title: "快速设置", subtitle: "调整演示世界的核心参数" }, {
                    default: settingsForm
                }),
                mc("mc-panel", { title: "发布操作", subtitle: "所有操作均在本地 Demo 内完成" }, {
                    default: function () {
                        return Vue.h("div", { class: "demo-panel-body" }, [
                            Vue.h("p", { style: { margin: "0", color: "#c6ceca" } }, "保存设置后可生成客户预览。发布流程会显示确认、加载遮罩与完成提示。"),
                            Vue.h("div", { class: "demo-footer-actions" }, [
                                button("保存设置", "normal", saveSettings, { id: "demo-save-settings", "data-demo-action": "save-settings" }),
                                mc("mc-tooltip", { content: "检查资源后发布当前通道" }, {
                                    default: function () {
                                        return button("发布当前版本", "primary", function () { state.confirm = true; }, { id: "demo-publish", "data-demo-action": "publish-confirm" });
                                    }
                                })
                            ])
                        ]);
                    }
                })
            ])
        ]);
    }

    function settingsForm() {
        return Vue.h("div", { class: "demo-form-grid" }, [
            mc("mc-form-field", { label: "世界名称", description: "显示在客户预览与发布记录中。" }, {
                default: function () {
                    return mc("mc-text-field", { id: "demo-world-name", "data-demo-control": "world-name", modelValue: state.worldName, placeholder: "输入世界名称", "onUpdate:modelValue": function (value) { state.worldName = value; } });
                }
            }),
            mc("mc-form-field", { label: "世界种子", description: "用于复现演示环境。" }, {
                default: function () {
                    return mc("mc-text-field", { modelValue: state.seed, placeholder: "输入种子", "onUpdate:modelValue": function (value) { state.seed = value; } });
                }
            }),
            mc("mc-form-field", { label: "难度", description: "选择进入世界后的默认难度。" }, {
                default: function () {
                    return mc("mc-dropdown", { id: "demo-difficulty", "data-demo-control": "difficulty", options: difficulties, modelValue: state.difficulty, unselectedText: "选择难度", "onUpdate:modelValue": function (value) { state.difficulty = value; } });
                }
            }),
            mc("mc-form-field", { label: "游戏模式", description: "RadioGroup 维护互斥模式。" }, {
                default: function () {
                    return mc("mc-radio-group", { modelValue: state.mode, options: modes, "onUpdate:modelValue": function (value) { state.mode = value; } });
                }
            }),
            Vue.h("div", { class: "demo-form-wide" }, [
                Vue.h("div", { class: "demo-setting-row" }, [
                    Vue.h("div", { class: "demo-setting-copy" }, [Vue.h("strong", null, "启用操作音效"), Vue.h("span", null, "保留按钮、列表与弹层的 Ore 反馈音。")]),
                    mc("mc-switch", { id: "demo-sound", "data-demo-control": "sound", modelValue: state.sound, "onUpdate:modelValue": function (value) { state.sound = value; if (Mc.setSoundEnabled) Mc.setSoundEnabled(value); } })
                ]),
                Vue.h("div", { class: "demo-setting-row" }, [
                    Vue.h("div", { class: "demo-setting-copy" }, [Vue.h("strong", null, "发布前创建备份"), Vue.h("span", null, "在版本切换前保留当前世界快照。")]),
                    mc("mc-checkbox", { modelValue: state.backup, "onUpdate:modelValue": function (value) { state.backup = value; } }, { default: function () { return "自动备份"; } })
                ]),
                Vue.h("div", { class: "demo-setting-row" }, [
                    Vue.h("div", { class: "demo-setting-copy" }, [Vue.h("strong", null, "默认发布目标"), Vue.h("span", null, "将当前世界设为工作台默认目标。")]),
                    Vue.h("div", { class: "demo-inline-options" }, [
                        mc("mc-radio", { modelValue: state.preferredMode, value: "survival", "onUpdate:modelValue": function (value) { state.preferredMode = value; } }, { default: function () { return "生存"; } }),
                        mc("mc-radio", { modelValue: state.preferredMode, value: "creative", "onUpdate:modelValue": function (value) { state.preferredMode = value; } }, { default: function () { return "创造"; } })
                    ])
                ]),
                Vue.h("div", { class: "demo-panel-body" }, [
                    Vue.h("div", { class: "demo-slider-label" }, [Vue.h("span", null, "模拟距离"), Vue.h("strong", null, state.simulationDistance + "%")]),
                    mc("mc-slider", { id: "demo-simulation-distance", "data-demo-control": "simulation-distance", modelValue: state.simulationDistance, min: 0, max: 100, step: 1, tip: true, "onUpdate:modelValue": function (value) { state.simulationDistance = value; } })
                ])
            ])
        ]);
    }

    function workspaceContent() {
        if (state.workspace === "settings") {
            return mc("mc-panel", { title: "世界设置", subtitle: "完整配置将在保存后写入当前 Demo 状态" }, { default: settingsForm });
        }
        if (state.workspace === "history") {
            return mc("mc-panel", { title: "发布记录", subtitle: "最近的离线发布与客户预览" }, {
                default: function () {
                    return Vue.h("div", { class: "demo-list-region" }, [mc("mc-scroll-view", null, { default: worldList })]);
                }
            });
        }
        return overviewPanel();
    }

    function drawer() {
        return mc("mc-drawer", { open: state.drawer, title: "工作区导航", placement: "left", "onUpdate:open": function (value) { state.drawer = value; } }, {
            default: function () {
                return Vue.h("div", { class: "demo-drawer-content" }, [
                    mc("mc-header", { title: "Ore 工作区" }),
                    mc("mc-list", { modelValue: state.workspace, mode: "single", onChange: function (value) { state.workspace = value; state.drawer = false; } }, {
                        default: function () {
                            return [
                                mc("mc-list-item", { label: "工作台", subtitle: "总览与发布", value: "overview", icon: "mc-home" }),
                                mc("mc-list-item", { label: "世界设置", subtitle: "表单与参数", value: "settings", icon: "mc-world" }),
                                mc("mc-list-item", { label: "发布记录", subtitle: "历史与状态", value: "history", icon: "mc-clipboard" })
                            ];
                        }
                    }),
                    Vue.h("p", { class: "demo-drawer-note" }, "该导航抽屉、列表选择和页面切换均由真实 McUI 组件驱动。")
                ]);
            }
        });
    }

    function overlays() {
        return [
            mc("mc-modal", { open: state.modal, title: "客户预览版本", "onUpdate:open": function (value) { state.modal = value; } }, {
                default: function () {
                    return Vue.h("div", { class: "demo-modal-content" }, [
                        Vue.h("p", { style: { margin: "0", color: "#d3dad6" } }, "当前版本展示 Ore 工作台的完整业务流程，所有资源已内联。"),
                        Vue.h("div", { class: "demo-modal-facts" }, [
                            Vue.h("div", { class: "demo-modal-fact" }, [Vue.h("strong", null, "32"), Vue.h("span", null, "真实 UI 元素")]),
                            Vue.h("div", { class: "demo-modal-fact" }, [Vue.h("strong", null, "0"), Vue.h("span", null, "外部依赖")]),
                            Vue.h("div", { class: "demo-modal-fact" }, [Vue.h("strong", null, "1"), Vue.h("span", null, "客户交付文件")])
                        ]),
                        statusLog()
                    ]);
                }
            }),
            mc("mc-confirm", { open: state.confirm, title: "发布当前版本？", "onUpdate:open": function (value) { state.confirm = value; }, onConfirm: beginPublish }, {
                default: function () { return "将发布“" + state.worldName + "”到“" + state.releaseChannel + "”通道。"; }
            }),
            drawer(),
            mc("mc-loading-mask", { visible: state.loading, text: "正在生成客户预览" }),
            mc("mc-pop-host")
        ];
    }

    var Root = {
        render: function () {
            return Vue.h("div", { class: "demo-app" }, [
                mc("mc-layout", null, {
                    default: function () {
                        return [
                            appbar(),
                            mc("mc-scroll-view", null, {
                                default: function () {
                                    return Vue.h("div", { class: "demo-page" }, [
                                        Vue.h("div", { class: "demo-content" }, [
                                            Vue.h("section", { class: "demo-hero" }, [
                                                Vue.h("div", { class: "demo-hero-copy" }, [
                                                    Vue.h("p", { class: "demo-kicker" }, "Ore UI / World Workspace"),
                                                    Vue.h("h1", null, "把世界配置、验证与发布放进一个工作台。"),
                                                    Vue.h("p", null, "这是一套可直接操作的 Ore 风格前端 Demo：不是组件目录，而是完整的世界管理与客户交付流程。"),
                                                    Vue.h("div", { class: "demo-actions" }, [
                                                        button("打开工作区导航", "normal", function () { state.drawer = true; }, { "data-demo-action": "open-drawer-secondary" }),
                                                        button("发布客户预览", "primary", function () { state.confirm = true; }, { "data-demo-action": "publish-confirm-secondary" })
                                                    ])
                                                ]),
                                                Vue.h("div", { class: "demo-hero-status" }, [
                                                    mc("mc-icon", { name: "mc-world", size: "54" }),
                                                    Vue.h("div", null, [Vue.h("strong", null, "LOCAL READY"), Vue.h("span", null, "纯 Java / 离线资源 / 单文件交付")])
                                                ])
                                            ]),
                                            Vue.h("section", { class: "demo-metrics", "aria-label": "工作台摘要" }, [
                                                metricCard("活跃世界", "已加载并可编辑", "3", "全部通过资源校验", function () { state.workspace = "overview"; }),
                                                metricCard("发布准备度", "当前稳定通道", state.buildProgress + "%", "交互与资源已就绪", function () { state.modal = true; }),
                                                metricCard("自动备份", "最近一次快照", state.backup ? "ON" : "OFF", "切换前保留世界状态", function () { state.backup = !state.backup; }),
                                                metricCard("当前通道", "客户预览目标", state.releaseChannel.toUpperCase(), "可在工作台内切换", function () { state.workspace = "settings"; })
                                            ]),
                                            Vue.h("section", { id: "workspace", class: "demo-workspace" }, [
                                                mc("mc-tabs", { modelValue: state.workspace, items: workspaceTabs, "onUpdate:modelValue": function (value) { state.workspace = value; } }, {
                                                    default: function () { return Vue.h("div", { class: "demo-tabs-content" }, [workspaceContent()]); }
                                                })
                                            ])
                                        ])
                                    ]);
                                }
                            })
                        ].concat(overlays());
                    }
                })
            ]);
        }
    };

    var root = document.getElementById("customer-demo-root");
    var app = Vue.createApp(Root);
    app.use(Mc.default);
    app.mount(root);
    var mountedComponents = Object.keys(renderedComponents).sort();
    document.body.setAttribute("data-customer-demo-ready", "true");
    document.body.setAttribute("data-customer-demo-mounted-components", String(mountedComponents.length));
    window.__AUI_CUSTOMER_DEMO__ = {
        state: state,
        components: Object.keys(components).sort(),
        mountedComponents: mountedComponents
    };
})();
