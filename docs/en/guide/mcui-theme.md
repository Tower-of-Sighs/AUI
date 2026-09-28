# McUI Theme

McUI is a standalone pure-CSS theme following the [built-in theme contract](../../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/theme-spec.md). It implements the same shared component classes, state classes, and token names as Ore. To switch a business page, change only the stylesheet and root scope class; keep its DOM and interaction code.

    <link rel="stylesheet" href="/apricityui/theme/mcui/mcui.css">
    <body class="mcui-theme">

The entry point is apricityui/theme/mcui/mcui.css, with local fonts. The theme does not require Vue or a browser engine. The stylesheet is MPL-2.0; the bundled McUI fonts retain their MIT attribution in the same directory.

Open apricityui/theme/mcui/example.html in game for the seven-page pure-CSS showcase. The separate apricityui/theme/mcui/vue-example.html page loads the upstream 2.0.0 visual Gallery with 68 components from `/apricityui/runtime/mcui/` and its `.mc-theme`; it is not a reskin of `mcui.css`. Pure-CSS pages need only the stylesheet above.
