function _typeof(o) { "@babel/helpers - typeof"; return _typeof = "function" == typeof Symbol && "symbol" == typeof Symbol.iterator ? function (o) { return typeof o; } : function (o) { return o && "function" == typeof Symbol && o.constructor === Symbol && o !== Symbol.prototype ? "symbol" : typeof o; }, _typeof(o); }
var McUIVisualGallery;
function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }
function _toPropertyKey(t) { var i; i = _toPrimitive(t, "string"); return "symbol" == _typeof(i) ? i : i + ""; }
function _toPrimitive(t, r) { var e, i; if ("object" != _typeof(t) || !t) return t; e = t[Symbol.toPrimitive]; if (void 0 !== e) { i = e.call(t, r || "default"); if ("object" != _typeof(i)) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === r ? String : Number)(t); }
function _toConsumableArray(r) { return _arrayWithoutHoles(r) || _iterableToArray(r) || _unsupportedIterableToArray(r) || _nonIterableSpread(); }
function _nonIterableSpread() { throw new TypeError("Invalid attempt to spread non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method."); }
function _unsupportedIterableToArray(r, a) { var t; if (r) { if ("string" == typeof r) return _arrayLikeToArray(r, a); t = {}.toString.call(r).slice(8, -1); return "Object" === t && r.constructor && (t = r.constructor.name), "Map" === t || "Set" === t ? Array.from(r) : "Arguments" === t || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(t) ? _arrayLikeToArray(r, a) : void 0; } }
function _iterableToArray(r) { if ("undefined" != typeof Symbol && null != r[Symbol.iterator] || null != r["@@iterator"]) return Array.from(r); }
function _arrayWithoutHoles(r) { if (Array.isArray(r)) return _arrayLikeToArray(r); }
function _arrayLikeToArray(r, a) { var e, n; (null == a || a > r.length) && (a = r.length); for (e = 0, n = Array(a); e < a; e++) n[e] = r[e]; return n; }
McUIVisualGallery = function (n, e) {
  "use strict";

  var F, G, R, W, H, O, q, j, $, J, K, Q, X, Y, Z, v, ee, te, oe, le, ae, ne, re, de, se, ce, me, ie;
  F = {
    class: "gallery-section"
  };
  G = {
    class: "gallery-grid"
  };
  R = {
    class: "gallery-section gallery-states"
  };
  W = {
    class: "gallery-row"
  };
  H = {
    class: "gallery-grid gallery-grid--wide"
  };
  O = {
    class: "gallery-row"
  };
  q = {
    class: "gallery-row"
  };
  j = {
    class: "gallery-section"
  };
  $ = {
    class: "gallery-grid gallery-grid--forms"
  };
  J = ["id"];
  K = {
    class: "gallery-control-stack"
  };
  Q = {
    class: "gallery-control-stack"
  };
  X = {
    class: "gallery-section"
  };
  Y = {
    class: "gallery-layout-shell"
  };
  Z = {
    class: "gallery-grid gallery-grid--wide"
  };
  v = {
    class: "gallery-tab-panel"
  };
  ee = {
    class: "gallery-tab-panel"
  };
  te = {
    class: "gallery-list-host"
  };
  oe = {
    class: "gallery-section"
  };
  le = {
    class: "gallery-grid gallery-grid--wide gallery-flow-host"
  };
  ae = {
    class: "gallery-virtual-row"
  };
  ne = {
    class: "gallery-tab-panel"
  };
  re = {
    class: "gallery-section gallery-overlays"
  };
  de = {
    class: "gallery-grid gallery-grid--wide"
  };
  se = {
    class: "gallery-panel-host"
  };
  ce = {
    class: "gallery-section"
  };
  me = {
    class: "gallery-grid gallery-grid--wide"
  };
  ie = e.defineComponent({
    __name: "VisualGallery",
    setup: function setup(St) {
      var i, p, V, g, _, N, y, C, f, x, b, w, r, d, M, E, T, u, S, h, pe, k, s, U, P, D, c, Ve, ge, _e, Ne, ye, Ce, fe;
      i = McUIVue.usePop();
      p = e.ref(!0);
      V = e.ref(!0);
      g = e.ref("survival");
      _ = e.ref("creative");
      N = e.ref(62);
      y = e.ref("Alex");
      C = e.ref("A quiet seed near a village.");
      f = e.ref("survival");
      x = e.ref("creative");
      b = e.ref(12);
      w = e.ref(null);
      r = e.ref("worlds");
      d = e.ref("play");
      M = e.ref(["survival"]);
      E = e.ref(2);
      T = e.ref([1]);
      u = e.ref({
        page: 1,
        itemsPerPage: 2,
        sortBy: [],
        search: ""
      });
      S = new URLSearchParams(window.location.search).get("data-table-state");
      h = new URLSearchParams(window.location.search).get("data-table-loading-height");
      pe = h === "px" ? "320px" : h === "rows" ? "3L" : void 0;
      k = e.ref("graphics");
      s = e.ref("world");
      U = e.ref(!1);
      P = e.ref(!0);
      D = e.ref(!0);
      c = [{
        title: "Survival",
        value: "survival"
      }, {
        title: "Creative",
        value: "creative"
      }, {
        title: "Adventure",
        value: "adventure",
        disabled: !0
      }];
      Ve = [{
        label: "Worlds",
        value: "worlds"
      }, {
        label: "Servers",
        value: "servers"
      }];
      ge = [{
        label: "Play",
        value: "play"
      }, {
        label: "Settings",
        value: "settings",
        color: "#3d75a5"
      }, {
        label: "Marketplace",
        value: "marketplace"
      }, {
        label: "Experiments",
        value: "experiments",
        disabled: !0
      }];
      _e = [{
        title: "Player",
        key: "name"
      }, {
        title: "Score",
        key: "score"
      }];
      Ne = [{
        id: 1,
        name: "Alex",
        score: 8
      }, {
        id: 2,
        name: "Steve",
        score: 12
      }, {
        id: 3,
        name: "Creeper",
        score: 4
      }];
      ye = Array.from({
        length: 40
      }, function (xe, t) {
        return "Chunk ".concat(t + 1);
      });
      Ce = [{
        title: "Worlds",
        href: "#worlds"
      }, {
        title: "New world"
      }];
      fe = [{
        title: "World",
        value: "world",
        editable: !0
      }, {
        title: "Rules",
        value: "rules",
        editable: !0
      }, {
        title: "Create",
        value: "create"
      }];
      return e.onMounted(function () {
        i.show("Gallery pop", 6e4, "success");
      }), e.onBeforeUnmount(i.clear), function (xe, t) {
        var a, be, l, we, Me, Ee, Te, ue, Se, he, ke, Ue, Pe, De, Le, Ae, Be, ze, Ie, Fe, Ge, Re, We, L, He, Oe, qe, je, $e, Je, Ke, Qe, m, A, B, Xe, Ye, Ze, ve, et, tt, ot, lt, at, nt, rt, dt, z, st, ct, mt, it, pt, Vt, gt, I, _t, Nt, yt, Ct, ft, xt, bt, wt, Mt, Et, Tt, ut;
        a = e.resolveComponent("mc-panel");
        be = e.resolveComponent("mc-theme-provider");
        l = e.resolveComponent("mc-button");
        we = e.resolveComponent("mc-defaults-provider");
        Me = e.resolveComponent("mc-locale-provider");
        Ee = e.resolveComponent("mc-alert");
        Te = e.resolveComponent("mc-card-title");
        ue = e.resolveComponent("mc-card-subtitle");
        Se = e.resolveComponent("mc-card-item");
        he = e.resolveComponent("mc-card-text");
        ke = e.resolveComponent("mc-card-actions");
        Ue = e.resolveComponent("mc-card");
        Pe = e.resolveComponent("mc-badge");
        De = e.resolveComponent("mc-avatar");
        Le = e.resolveComponent("mc-chip");
        Ae = e.resolveComponent("mc-divider");
        Be = e.resolveComponent("mc-tooltip");
        ze = e.resolveComponent("mc-skeleton");
        Ie = e.resolveComponent("mc-progress");
        Fe = e.resolveComponent("mc-spinner");
        Ge = e.resolveComponent("mc-icon");
        Re = e.resolveComponent("mc-form");
        We = e.resolveComponent("mc-form-field");
        L = e.resolveComponent("mc-text-field");
        He = e.resolveComponent("mc-textarea");
        Oe = e.resolveComponent("mc-select");
        qe = e.resolveComponent("mc-autocomplete");
        je = e.resolveComponent("mc-file-input");
        $e = e.resolveComponent("mc-number-input");
        Je = e.resolveComponent("mc-checkbox");
        Ke = e.resolveComponent("mc-radio");
        Qe = e.resolveComponent("mc-radio-group");
        m = e.resolveComponent("mc-switch");
        A = e.resolveComponent("mc-slider");
        B = e.resolveComponent("mc-col");
        Xe = e.resolveComponent("mc-spacer");
        Ye = e.resolveComponent("mc-row");
        Ze = e.resolveComponent("mc-container");
        ve = e.resolveComponent("mc-header");
        et = e.resolveComponent("mc-appbar-button");
        tt = e.resolveComponent("mc-appbar-icon");
        ot = e.resolveComponent("mc-appbar");
        lt = e.resolveComponent("mc-drawer");
        at = e.resolveComponent("mc-main");
        nt = e.resolveComponent("mc-layout");
        rt = e.resolveComponent("mc-tabs");
        dt = e.resolveComponent("mc-button-tabs");
        z = e.resolveComponent("mc-list-item");
        st = e.resolveComponent("mc-list");
        ct = e.resolveComponent("mc-scroll-view");
        mt = e.resolveComponent("mc-pagination");
        it = e.resolveComponent("mc-table");
        pt = e.resolveComponent("mc-data-table");
        Vt = e.resolveComponent("mc-virtual-scroll");
        gt = e.resolveComponent("mc-breadcrumbs");
        I = e.resolveComponent("mc-expansion-panel");
        _t = e.resolveComponent("mc-expansion-panels");
        Nt = e.resolveComponent("mc-stepper");
        yt = e.resolveComponent("mc-overlay");
        Ct = e.resolveComponent("mc-dialog");
        ft = e.resolveComponent("mc-menu");
        xt = e.resolveComponent("mc-snackbar");
        bt = e.resolveComponent("mc-confirm");
        wt = e.resolveComponent("mc-loading-mask");
        Mt = e.resolveComponent("mc-pop-host");
        Et = e.resolveComponent("mc-formatted-text");
        Tt = e.resolveComponent("mc-tcode");
        ut = e.resolveComponent("mc-app");
        return e.openBlock(), e.createBlock(ut, {
          class: "visual-gallery",
          "data-gallery-component": "McApp"
        }, {
          default: e.withCtx(function () {
            return [t[73] || (t[73] = e.createElementVNode("header", {
              class: "gallery-hero"
            }, [e.createElementVNode("p", null, "mcui-oreui · Spectrollay reference 0bf8f466"), e.createElementVNode("h1", null, "Ore UI visual regression gallery"), e.createElementVNode("p", null, "68 public components · optional Minecraft fonts loaded explicitly")], -1)), e.createElementVNode("section", F, [t[25] || (t[25] = e.createElementVNode("h2", null, "Providers and foundations", -1)), e.createElementVNode("div", G, [e.createVNode(be, {
              "data-gallery-component": "McThemeProvider",
              name: "gallery-green",
              theme: {
                colors: {
                  primary: "#3c8527"
                }
              }
            }, {
              default: e.withCtx(function () {
                return [e.createVNode(a, {
                  title: "ThemeProvider"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[22] || (t[22] = [e.createTextVNode("Scoped primary color", -1)]));
                  }),
                  _: 1
                })];
              }),
              _: 1
            }), e.createVNode(we, {
              "data-gallery-component": "McDefaultsProvider",
              defaults: {
                components: {
                  McButton: {
                    variant: "primary"
                  }
                }
              }
            }, {
              default: e.withCtx(function () {
                return [e.createVNode(l, null, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[23] || (t[23] = [e.createTextVNode("DefaultsProvider", -1)]));
                  }),
                  _: 1
                })];
              }),
              _: 1
            }), e.createVNode(Me, {
              "data-gallery-component": "McLocaleProvider",
              locale: "en",
              tag: "div"
            }, {
              default: e.withCtx(function () {
                return [e.createVNode(a, {
                  title: "LocaleProvider"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[24] || (t[24] = [e.createTextVNode("English / LTR scope", -1)]));
                  }),
                  _: 1
                })];
              }),
              _: 1
            })])]), e.createElementVNode("section", R, [t[42] || (t[42] = e.createElementVNode("h2", null, "Controls and feedback states", -1)), e.createElementVNode("div", W, [e.createVNode(l, {
              "data-gallery-component": "McButton",
              variant: "primary",
              "data-state": "hover-button"
            }, {
              default: e.withCtx(function () {
                return _toConsumableArray(t[26] || (t[26] = [e.createTextVNode("Primary", -1)]));
              }),
              _: 1
            }), e.createVNode(l, {
              variant: "normal",
              "data-state": "active-button"
            }, {
              default: e.withCtx(function () {
                return _toConsumableArray(t[27] || (t[27] = [e.createTextVNode("Active target", -1)]));
              }),
              _: 1
            }), e.createVNode(l, {
              variant: "error"
            }, {
              default: e.withCtx(function () {
                return _toConsumableArray(t[28] || (t[28] = [e.createTextVNode("Delete", -1)]));
              }),
              _: 1
            }), e.createVNode(l, {
              disabled: ""
            }, {
              default: e.withCtx(function () {
                return _toConsumableArray(t[29] || (t[29] = [e.createTextVNode("Disabled", -1)]));
              }),
              _: 1
            }), e.createVNode(l, {
              loading: ""
            }, {
              default: e.withCtx(function () {
                return _toConsumableArray(t[30] || (t[30] = [e.createTextVNode("Loading", -1)]));
              }),
              _: 1
            })]), e.createElementVNode("div", H, [e.createVNode(Ee, {
              "data-gallery-component": "McAlert",
              variant: "success",
              closable: ""
            }, {
              title: e.withCtx(function () {
                return _toConsumableArray(t[31] || (t[31] = [e.createTextVNode("Saved", -1)]));
              }),
              default: e.withCtx(function () {
                return [t[32] || (t[32] = e.createTextVNode("The world is ready.", -1))];
              }),
              _: 1
            }), e.createVNode(Ue, {
              "data-gallery-component": "McCard"
            }, {
              default: e.withCtx(function () {
                return [e.createVNode(Se, {
                  "data-gallery-component": "McCardItem"
                }, {
                  default: e.withCtx(function () {
                    return [e.createVNode(Te, {
                      "data-gallery-component": "McCardTitle"
                    }, {
                      default: e.withCtx(function () {
                        return _toConsumableArray(t[33] || (t[33] = [e.createTextVNode("Playable card", -1)]));
                      }),
                      _: 1
                    }), e.createVNode(ue, {
                      "data-gallery-component": "McCardSubtitle"
                    }, {
                      default: e.withCtx(function () {
                        return _toConsumableArray(t[34] || (t[34] = [e.createTextVNode("Compound structure", -1)]));
                      }),
                      _: 1
                    })];
                  }),
                  _: 1
                }), e.createVNode(he, {
                  "data-gallery-component": "McCardText"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[35] || (t[35] = [e.createTextVNode("Layered Ore UI panel.", -1)]));
                  }),
                  _: 1
                }), e.createVNode(ke, {
                  "data-gallery-component": "McCardActions"
                }, {
                  default: e.withCtx(function () {
                    return [e.createVNode(l, {
                      size: "small"
                    }, {
                      default: e.withCtx(function () {
                        return _toConsumableArray(t[36] || (t[36] = [e.createTextVNode("Open", -1)]));
                      }),
                      _: 1
                    })];
                  }),
                  _: 1
                })];
              }),
              _: 1
            }), e.createElementVNode("div", O, [e.createVNode(Pe, {
              "data-gallery-component": "McBadge",
              content: "3"
            }, {
              default: e.withCtx(function () {
                return [e.createVNode(l, {
                  size: "small"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[37] || (t[37] = [e.createTextVNode("Inbox", -1)]));
                  }),
                  _: 1
                })];
              }),
              _: 1
            }), e.createVNode(De, {
              "data-gallery-component": "McAvatar",
              text: "MC",
              size: "large",
              color: "#3c8527"
            }), e.createVNode(Le, {
              "data-gallery-component": "McChip",
              selected: "",
              closable: ""
            }, {
              default: e.withCtx(function () {
                return _toConsumableArray(t[38] || (t[38] = [e.createTextVNode("Survival", -1)]));
              }),
              _: 1
            }), e.createVNode(Ae, {
              "data-gallery-component": "McDivider",
              vertical: ""
            }), e.createVNode(Be, {
              "data-gallery-component": "McTooltip"
            }, {
              content: e.withCtx(function () {
                return _toConsumableArray(t[40] || (t[40] = [e.createElementVNode("strong", null, "Tooltip title", -1), e.createElementVNode("br", null, null, -1), e.createTextVNode("Tooltip text", -1)]));
              }),
              default: e.withCtx(function () {
                return [e.createVNode(l, {
                  size: "small",
                  "data-state": "tooltip-target"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[39] || (t[39] = [e.createTextVNode("Hover hint", -1)]));
                  }),
                  _: 1
                })];
              }),
              _: 1
            })]), e.createVNode(ze, {
              "data-gallery-component": "McSkeleton",
              height: "38",
              "aria-label": "Loading preview"
            }), e.createVNode(Ie, {
              "data-gallery-component": "McProgress",
              value: 68,
              label: "Generating terrain",
              variant: "success"
            }), e.createElementVNode("div", q, [e.createVNode(Fe, {
              "data-gallery-component": "McSpinner",
              size: 34
            }), t[41] || (t[41] = e.createElementVNode("span", null, "Loading resources", -1))]), e.createVNode(Ge, {
              "data-gallery-component": "McIcon",
              path: "M4 4h16v16H4zM8 8h8v8H8z",
              color: "#ffffff",
              "aria-label": "Pixel square"
            })])]), e.createElementVNode("section", j, [t[44] || (t[44] = e.createElementVNode("h2", null, "Form system", -1)), e.createElementVNode("div", $, [e.createVNode(Re, {
              "data-gallery-component": "McForm"
            }, {
              default: e.withCtx(function () {
                return [e.createVNode(l, {
                  type: "submit",
                  size: "small"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[43] || (t[43] = [e.createTextVNode("Validate form", -1)]));
                  }),
                  _: 1
                })];
              }),
              _: 1
            }), e.createVNode(We, {
              "data-gallery-component": "McFormField",
              label: "FormField",
              description: "Low-level field wrapper",
              hint: "Hint text"
            }, {
              default: e.withCtx(function (o) {
                return [e.createElementVNode("input", {
                  id: o.id,
                  class: "mc-input",
                  value: "Native control"
                }, null, 8, J)];
              }),
              _: 1
            }), e.createVNode(L, {
              "data-gallery-component": "McTextField",
              modelValue: y.value,
              "onUpdate:modelValue": t[0] || (t[0] = function (o) {
                return y.value = o;
              }),
              label: "Player name",
              description: "Shown to other players",
              hint: "Letters and numbers",
              "data-state": "focus-input"
            }, null, 8, ["modelValue"]), e.createVNode(L, {
              label: "Error state",
              "model-value": "Invalid",
              "error-messages": ["Name already exists"]
            }), e.createVNode(He, {
              "data-gallery-component": "McTextarea",
              modelValue: C.value,
              "onUpdate:modelValue": t[1] || (t[1] = function (o) {
                return C.value = o;
              }),
              label: "World description",
              rows: 3
            }, null, 8, ["modelValue"]), e.createVNode(Oe, {
              "data-gallery-component": "McSelect",
              modelValue: f.value,
              "onUpdate:modelValue": t[2] || (t[2] = function (o) {
                return f.value = o;
              }),
              label: "Game mode",
              options: c
            }, null, 8, ["modelValue"]), e.createVNode(qe, {
              "data-gallery-component": "McAutocomplete",
              modelValue: x.value,
              "onUpdate:modelValue": t[3] || (t[3] = function (o) {
                return x.value = o;
              }),
              label: "Search mode",
              options: c
            }, null, 8, ["modelValue"]), e.createVNode(je, {
              "data-gallery-component": "McFileInput",
              modelValue: w.value,
              "onUpdate:modelValue": t[4] || (t[4] = function (o) {
                return w.value = o;
              }),
              label: "Resource pack",
              accept: ".zip"
            }, null, 8, ["modelValue"]), e.createVNode($e, {
              "data-gallery-component": "McNumberInput",
              modelValue: b.value,
              "onUpdate:modelValue": t[5] || (t[5] = function (o) {
                return b.value = o;
              }),
              label: "Render distance",
              min: 2,
              max: 32
            }, null, 8, ["modelValue"]), e.createVNode(Je, {
              "data-gallery-component": "McCheckbox",
              modelValue: p.value,
              "onUpdate:modelValue": t[6] || (t[6] = function (o) {
                return p.value = o;
              }),
              label: "Allow cheats",
              description: "Cannot be undone"
            }, null, 8, ["modelValue"]), e.createVNode(Ke, {
              "data-gallery-component": "McRadio",
              modelValue: g.value,
              "onUpdate:modelValue": t[7] || (t[7] = function (o) {
                return g.value = o;
              }),
              value: "survival",
              label: "Survival",
              description: "Standard progression"
            }, null, 8, ["modelValue"]), e.createVNode(Qe, {
              "data-gallery-component": "McRadioGroup",
              modelValue: _.value,
              "onUpdate:modelValue": t[8] || (t[8] = function (o) {
                return _.value = o;
              }),
              label: "Default mode",
              description: "Choose one",
              options: c.slice(0, 2)
            }, null, 8, ["modelValue", "options"]), e.createElementVNode("div", K, [e.createVNode(m, {
              "data-gallery-component": "McSwitch",
              modelValue: V.value,
              "onUpdate:modelValue": t[9] || (t[9] = function (o) {
                return V.value = o;
              }),
              label: "Enable sound"
            }, null, 8, ["modelValue"]), e.createVNode(m, {
              "model-value": !1,
              label: "Sound off"
            }), e.createVNode(m, {
              disabled: "",
              label: "Disabled switch"
            })]), e.createElementVNode("div", Q, [e.createVNode(A, {
              "data-gallery-component": "McSlider",
              modelValue: N.value,
              "onUpdate:modelValue": t[10] || (t[10] = function (o) {
                return N.value = o;
              }),
              label: "Volume",
              description: "Master output",
              "show-value": ""
            }, null, 8, ["modelValue"]), e.createVNode(A, {
              "model-value": 35,
              "aria-label": "Disabled volume",
              disabled: ""
            })])])]), e.createElementVNode("section", X, [t[51] || (t[51] = e.createElementVNode("h2", null, "Layout and navigation", -1)), e.createVNode(Ze, {
              "data-gallery-component": "McContainer",
              fluid: ""
            }, {
              default: e.withCtx(function () {
                return [e.createVNode(Ye, {
                  "data-gallery-component": "McRow",
                  align: "center"
                }, {
                  default: e.withCtx(function () {
                    return [e.createVNode(B, {
                      "data-gallery-component": "McCol",
                      cols: 4
                    }, {
                      default: e.withCtx(function () {
                        return _toConsumableArray(t[45] || (t[45] = [e.createElementVNode("div", {
                          class: "gallery-cell"
                        }, "Col 4", -1)]));
                      }),
                      _: 1
                    }), e.createVNode(Xe, {
                      "data-gallery-component": "McSpacer"
                    }), e.createVNode(B, {
                      cols: 4
                    }, {
                      default: e.withCtx(function () {
                        return _toConsumableArray(t[46] || (t[46] = [e.createElementVNode("div", {
                          class: "gallery-cell"
                        }, "Col 4", -1)]));
                      }),
                      _: 1
                    })];
                  }),
                  _: 1
                })];
              }),
              _: 1
            }), e.createVNode(ve, {
              "data-gallery-component": "McHeader",
              title: "Header component"
            }), e.createElementVNode("div", Y, [e.createVNode(nt, {
              "data-gallery-component": "McLayout"
            }, {
              default: e.withCtx(function () {
                return [e.createVNode(ot, {
                  "data-gallery-component": "McAppbar",
                  title: "Worlds",
                  fixed: !1
                }, {
                  right: e.withCtx(function () {
                    return [e.createVNode(et, {
                      "data-gallery-component": "McAppbarButton",
                      color: "#3d75a5"
                    }, {
                      default: e.withCtx(function () {
                        return _toConsumableArray(t[47] || (t[47] = [e.createTextVNode("Edit", -1)]));
                      }),
                      _: 1
                    }), e.createVNode(tt, {
                      "data-gallery-component": "McAppbarIcon",
                      "aria-label": "Settings"
                    })];
                  }),
                  _: 1
                }), e.createVNode(lt, {
                  "data-gallery-component": "McDrawer",
                  modelValue: D.value,
                  "onUpdate:modelValue": t[11] || (t[11] = function (o) {
                    return D.value = o;
                  }),
                  mode: "permanent",
                  title: "Navigation",
                  size: 190
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[48] || (t[48] = [e.createTextVNode("Worlds", -1), e.createElementVNode("br", null, null, -1), e.createTextVNode("Servers", -1), e.createElementVNode("br", null, null, -1), e.createTextVNode("Settings", -1)]));
                  }),
                  _: 1
                }, 8, ["modelValue"]), e.createVNode(at, {
                  "data-gallery-component": "McMain"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[49] || (t[49] = [e.createElementVNode("div", {
                      class: "gallery-layout-content"
                    }, "Main content respects registered layout bars.", -1)]));
                  }),
                  _: 1
                })];
              }),
              _: 1
            })]), e.createElementVNode("div", Z, [e.createVNode(rt, {
              "data-gallery-component": "McTabs",
              modelValue: r.value,
              "onUpdate:modelValue": t[12] || (t[12] = function (o) {
                return r.value = o;
              }),
              items: Ve
            }, {
              default: e.withCtx(function () {
                return [e.createElementVNode("div", v, "Selected: " + e.toDisplayString(r.value), 1)];
              }),
              _: 1
            }, 8, ["modelValue"]), e.createVNode(dt, {
              "data-gallery-component": "McButtonTabs",
              modelValue: d.value,
              "onUpdate:modelValue": t[13] || (t[13] = function (o) {
                return d.value = o;
              }),
              title: "Play",
              items: ge
            }, {
              default: e.withCtx(function () {
                return [e.createElementVNode("div", ee, "Selected: " + e.toDisplayString(d.value), 1)];
              }),
              _: 1
            }, 8, ["modelValue"]), e.createElementVNode("div", te, [e.createVNode(st, {
              "data-gallery-component": "McList",
              modelValue: M.value,
              "onUpdate:modelValue": t[14] || (t[14] = function (o) {
                return M.value = o;
              }),
              mode: "multiple"
            }, {
              default: e.withCtx(function () {
                return [e.createVNode(z, {
                  label: "Survival world",
                  subtitle: "Last played today",
                  value: "survival"
                }), e.createVNode(z, {
                  label: "Creative world",
                  value: "creative"
                })];
              }),
              _: 1
            }, 8, ["modelValue"])]), t[50] || (t[50] = e.createElementVNode("span", {
              hidden: "",
              "data-gallery-component": "McListItem"
            }, null, -1)), e.createVNode(ct, {
              "data-gallery-component": "McScrollView",
              class: "gallery-scroll"
            }, {
              default: e.withCtx(function () {
                return [(e.openBlock(), e.createElementBlock(e.Fragment, null, e.renderList(8, function (o) {
                  return e.createElementVNode("p", {
                    key: o
                  }, "Scrollable line " + e.toDisplayString(o), 1);
                }), 64))];
              }),
              _: 1
            })])]), e.createElementVNode("section", oe, [t[56] || (t[56] = e.createElementVNode("h2", null, "Data, navigation, and flow", -1)), e.createElementVNode("div", le, [e.createVNode(mt, {
              "data-gallery-component": "McPagination",
              modelValue: E.value,
              "onUpdate:modelValue": t[15] || (t[15] = function (o) {
                return E.value = o;
              }),
              length: 8,
              "show-first-last": "",
              "aria-label": "Gallery pages"
            }, null, 8, ["modelValue"]), e.createVNode(it, {
              "data-gallery-component": "McTable",
              striped: "",
              hover: ""
            }, {
              header: e.withCtx(function () {
                return _toConsumableArray(t[52] || (t[52] = [e.createElementVNode("tr", null, [e.createElementVNode("th", null, "Name"), e.createElementVNode("th", null, "Score")], -1)]));
              }),
              body: e.withCtx(function () {
                return _toConsumableArray(t[53] || (t[53] = [e.createElementVNode("tr", null, [e.createElementVNode("td", null, "Alex"), e.createElementVNode("td", null, "8")], -1), e.createElementVNode("tr", null, [e.createElementVNode("td", null, "Steve"), e.createElementVNode("td", null, "12")], -1)]));
              }),
              _: 1
            }), e.createVNode(pt, {
              "data-gallery-component": "McDataTable",
              modelValue: T.value,
              "onUpdate:modelValue": t[16] || (t[16] = function (o) {
                return T.value = o;
              }),
              options: u.value,
              "onUpdate:options": t[17] || (t[17] = function (o) {
                return u.value = o;
              }),
              headers: _e,
              items: e.unref(S) === "empty" ? [] : Ne,
              loading: e.unref(S) === "loading",
              "loading-height": e.unref(pe),
              "items-per-page-options": [2, 3],
              "no-data-text": "No players found",
              "show-select": ""
            }, null, 8, ["modelValue", "options", "items", "loading", "loading-height"]), e.createVNode(Vt, {
              "data-gallery-component": "McVirtualScroll",
              items: e.unref(ye),
              "item-height": 34,
              height: 150
            }, {
              default: e.withCtx(function (_ref) {
                var o;
                o = _ref.item;
                return [e.createElementVNode("div", ae, e.toDisplayString(o), 1)];
              }),
              _: 1
            }, 8, ["items"]), e.createVNode(gt, {
              "data-gallery-component": "McBreadcrumbs",
              items: Ce,
              "aria-label": "Gallery breadcrumb"
            }), e.createVNode(_t, {
              "data-gallery-component": "McExpansionPanels",
              modelValue: k.value,
              "onUpdate:modelValue": t[18] || (t[18] = function (o) {
                return k.value = o;
              })
            }, {
              default: e.withCtx(function () {
                return [e.createVNode(I, {
                  "data-gallery-component": "McExpansionPanel",
                  value: "graphics",
                  title: "Graphics"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[54] || (t[54] = [e.createTextVNode("Fancy leaves and clouds.", -1)]));
                  }),
                  _: 1
                }), e.createVNode(I, {
                  value: "audio",
                  title: "Audio"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[55] || (t[55] = [e.createTextVNode("Music and sound.", -1)]));
                  }),
                  _: 1
                })];
              }),
              _: 1
            }, 8, ["modelValue"]), e.createVNode(Nt, {
              "data-gallery-component": "McStepper",
              modelValue: s.value,
              "onUpdate:modelValue": t[19] || (t[19] = function (o) {
                return s.value = o;
              }),
              items: fe
            }, {
              default: e.withCtx(function () {
                return [e.createElementVNode("div", ne, "Step: " + e.toDisplayString(s.value), 1)];
              }),
              _: 1
            }, 8, ["modelValue"])])]), e.createElementVNode("section", re, [t[70] || (t[70] = e.createElementVNode("h2", null, "Overlay and feedback surfaces", -1)), e.createElementVNode("div", de, [e.createVNode(yt, {
              "data-gallery-component": "McOverlay",
              "model-value": !0,
              teleport: !1,
              scrim: !1,
              class: "gallery-static-overlay"
            }, {
              default: e.withCtx(function () {
                return [e.createVNode(a, {
                  title: "Overlay"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[57] || (t[57] = [e.createTextVNode("Direct static overlay content.", -1)]));
                  }),
                  _: 1
                })];
              }),
              _: 1
            }), e.createVNode(Ct, {
              "data-gallery-component": "McDialog",
              "model-value": !0,
              teleport: !1,
              title: "World settings",
              class: "gallery-dialog"
            }, {
              actions: e.withCtx(function () {
                return [e.createVNode(l, {
                  size: "small"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[58] || (t[58] = [e.createTextVNode("Save", -1)]));
                  }),
                  _: 1
                })];
              }),
              default: e.withCtx(function () {
                return [t[59] || (t[59] = e.createElementVNode("p", null, "Dialog body with focus-safe controls.", -1))];
              }),
              _: 1
            }), e.createElementVNode("div", null, [t[63] || (t[63] = e.createElementVNode("span", {
              hidden: "",
              "data-gallery-component": "McMenu"
            }, null, -1)), e.createVNode(ft, {
              modelValue: U.value,
              "onUpdate:modelValue": t[20] || (t[20] = function (o) {
                return U.value = o;
              }),
              teleport: !1,
              class: "gallery-menu"
            }, {
              activator: e.withCtx(function (_ref2) {
                var o;
                o = _ref2.props;
                return [e.createVNode(l, e.mergeProps(o, {
                  "data-state": "menu-target"
                }), {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[60] || (t[60] = [e.createTextVNode("Gallery Menu", -1)]));
                  }),
                  _: 1
                }, 16)];
              }),
              default: e.withCtx(function () {
                return [t[61] || (t[61] = e.createElementVNode("button", {
                  type: "button",
                  role: "menuitem"
                }, "Save world", -1)), t[62] || (t[62] = e.createElementVNode("button", {
                  type: "button",
                  role: "menuitem"
                }, "Delete world", -1))];
              }),
              _: 1
            }, 8, ["modelValue"])]), e.createVNode(xt, {
              "data-gallery-component": "McSnackbar",
              modelValue: P.value,
              "onUpdate:modelValue": t[21] || (t[21] = function (o) {
                return P.value = o;
              }),
              teleport: !1,
              timeout: 0,
              variant: "success",
              class: "gallery-snackbar"
            }, {
              action: e.withCtx(function () {
                return _toConsumableArray(t[64] || (t[64] = [e.createTextVNode("Undo", -1)]));
              }),
              default: e.withCtx(function () {
                return [t[65] || (t[65] = e.createTextVNode("World saved", -1))];
              }),
              _: 1
            }, 8, ["modelValue"]), e.createVNode(bt, {
              "data-gallery-component": "McConfirm",
              "model-value": !0,
              teleport: !1,
              title: "Delete world?",
              "confirm-text": "Delete",
              danger: "",
              class: "gallery-confirm"
            }, {
              default: e.withCtx(function () {
                return _toConsumableArray(t[66] || (t[66] = [e.createTextVNode("This action cannot be undone.", -1)]));
              }),
              _: 1
            }), e.createVNode(wt, {
              "data-gallery-component": "McLoadingMask",
              "model-value": !0,
              teleport: !1,
              text: "Loading chunks",
              class: "gallery-loading-mask"
            }), e.createVNode(Mt, {
              "data-gallery-component": "McPopHost",
              class: "gallery-pop-host"
            }), e.createElementVNode("div", se, [e.createVNode(a, {
              "data-gallery-component": "McPanel",
              class: "gallery-panel",
              title: "Workspace panel",
              subtitle: "Fixed regions with a scrolling body"
            }, {
              actions: e.withCtx(function () {
                return [e.createVNode(l, {
                  size: "small"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[67] || (t[67] = [e.createTextVNode("Refresh", -1)]));
                  }),
                  _: 1
                })];
              }),
              footer: e.withCtx(function () {
                return [e.createVNode(l, {
                  size: "small",
                  variant: "primary"
                }, {
                  default: e.withCtx(function () {
                    return _toConsumableArray(t[68] || (t[68] = [e.createTextVNode("Save", -1)]));
                  }),
                  _: 1
                })];
              }),
              default: e.withCtx(function () {
                return [t[69] || (t[69] = e.createElementVNode("div", {
                  class: "gallery-panel__content"
                }, [e.createElementVNode("div", null, "World settings"), e.createElementVNode("div", null, "Resource packs"), e.createElementVNode("div", null, "Behavior packs"), e.createElementVNode("div", null, "Experiments"), e.createElementVNode("div", null, "Multiplayer")], -1))];
              }),
              _: 1
            })])])]), e.createElementVNode("section", ce, [t[72] || (t[72] = e.createElementVNode("h2", null, "Minecraft-specific rendering", -1)), e.createElementVNode("div", me, [e.createVNode(Et, {
              "data-gallery-component": "McFormattedText",
              text: "§aGreen §lBold §rPlain"
            }), e.createVNode(Tt, {
              "data-gallery-component": "McTcode"
            }, {
              default: e.withCtx(function () {
                return _toConsumableArray(t[71] || (t[71] = [e.createTextVNode("§bDiamond §eGold", -1)]));
              }),
              _: 1
            })])])];
          }),
          _: 1
        });
      };
    }
  });
  return n.default = ie, Object.defineProperties(n, _defineProperty({
    __esModule: {
      value: !0
    }
  }, Symbol.toStringTag, {
    value: "Module"
  })), n;
}({}, Vue);
