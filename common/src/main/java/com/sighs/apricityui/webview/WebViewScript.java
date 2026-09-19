package com.sighs.apricityui.webview;

/**
 * Script snippets that stand in for the key injection API WebView2 does not have.
 *
 * <p>WebView2 exposes {@code ICoreWebView2CompositionController::SendMouseInput} but
 * nothing equivalent for the keyboard, so key handling is realised on the page side.
 * Two consequences shape this class:</p>
 *
 * <ul>
 *   <li>A synthesised {@code KeyboardEvent} never triggers a browser <em>default
 *       action</em> — dispatching {@code keydown} will not type a character. So the
 *       snippets dispatch the event <em>and</em> apply the default editing action
 *       themselves when the focused element is editable.</li>
 *   <li>Committed text arrives separately (through {@code charTyped} in the host
 *       framework, because the platform IME path is not wired), hence
 *       {@link #textScript}.</li>
 * </ul>
 *
 * <p>The script bodies are compile-time constants and all inputs are passed as call
 * arguments, so only the arguments need escaping.</p>
 */
final class WebViewScript {

    /** {@code (K,C,T,CT,SH,AL,MT,RP)} — key, code, event type, modifiers, repeat. */
    private static final String KEY_BODY = """
            (function(K,C,T,CT,SH,AL,MT,RP){\
            var d=document,t=d.activeElement||d.body||d.documentElement;\
            if(!t||!t.dispatchEvent)return;\
            var e=new KeyboardEvent(T,{key:K,code:C,bubbles:true,cancelable:true,repeat:RP,\
            ctrlKey:CT,shiftKey:SH,altKey:AL,metaKey:MT});\
            t.dispatchEvent(e);\
            if(T!=='keydown'||e.defaultPrevented||CT||AL||MT)return;\
            var n=(t.tagName||'').toLowerCase(),inp=(n==='input'||n==='textarea');\
            if(!inp&&t.isContentEditable!==true)return;\
            var s=t.selectionStart,q=t.selectionEnd;\
            var fire=function(){try{t.dispatchEvent(new Event('input',{bubbles:true}))}catch(x){}};\
            if(K==='Backspace'){\
            if(inp){if(s===q){if(s===0)return;s--}\
            t.value=t.value.slice(0,s)+t.value.slice(q);t.selectionStart=t.selectionEnd=s;fire()}\
            else{try{d.execCommand('delete',false);fire()}catch(x){}}\
            }else if(K==='Delete'){\
            if(inp){if(s===q){if(q>=t.value.length)return;q++}\
            t.value=t.value.slice(0,s)+t.value.slice(q);t.selectionStart=t.selectionEnd=s;fire()}\
            else{try{d.execCommand('forwardDelete',false);fire()}catch(x){}}\
            }else if(K==='Enter'){\
            if(inp&&n==='textarea'){t.value=t.value.slice(0,s)+'\\n'+t.value.slice(q);\
            t.selectionStart=t.selectionEnd=s+1;fire()}\
            else if(!inp){try{d.execCommand('insertLineBreak',false);fire()}catch(x){}}\
            }else if(K==='ArrowLeft'){\
            if(inp){t.selectionStart=t.selectionEnd=(s===q)?Math.max(0,s-1):s}\
            else{try{d.getSelection().modify('move','backward','character')}catch(x){}}\
            }else if(K==='ArrowRight'){\
            if(inp){t.selectionStart=t.selectionEnd=(s===q)?Math.min(t.value.length,q+1):q}\
            else{try{d.getSelection().modify('move','forward','character')}catch(x){}}\
            }else if(K==='Home'){\
            if(inp){t.selectionStart=t.selectionEnd=0}\
            else{try{d.getSelection().modify('move','backward','lineboundary')}catch(x){}}\
            }else if(K==='End'){\
            if(inp){t.selectionStart=t.selectionEnd=t.value.length}\
            else{try{d.getSelection().modify('move','forward','lineboundary')}catch(x){}}\
            }})""";

    /** {@code (T)} — committed text. */
    private static final String TEXT_BODY = """
            (function(T){\
            var d=document,t=d.activeElement||d.body||d.documentElement;\
            if(!t)return;\
            var n=(t.tagName||'').toLowerCase(),inp=(n==='input'||n==='textarea');\
            if(inp){\
            var s=(t.selectionStart==null)?t.value.length:t.selectionStart;\
            var q=(t.selectionEnd==null)?s:t.selectionEnd;\
            t.value=t.value.slice(0,s)+T+t.value.slice(q);\
            t.selectionStart=t.selectionEnd=s+T.length;\
            try{t.dispatchEvent(new Event('input',{bubbles:true}))}catch(x){}\
            }else if(t.isContentEditable===true){\
            try{d.execCommand('insertText',false,T);\
            t.dispatchEvent(new Event('input',{bubbles:true}))}catch(x){}}\
            })""";

    private WebViewScript() {
    }

    /**
     * @param type      {@code "keydown"} or {@code "keyup"}
     * @param key       DOM {@code KeyboardEvent.key}
     * @param code      DOM {@code KeyboardEvent.code}
     * @param modifiers GLFW modifier bits; only shift/control/alt/super are meaningful
     * @param repeat    whether this is an auto-repeat
     */
    static String keyScript(String type, String key, String code, int modifiers, boolean repeat) {
        return KEY_BODY + "(" + quote(key) + "," + quote(code) + "," + quote(type)
                + "," + bool(modifiers, MOD_CONTROL) + "," + bool(modifiers, MOD_SHIFT)
                + "," + bool(modifiers, MOD_ALT) + "," + bool(modifiers, MOD_SUPER)
                + "," + (repeat ? "true" : "false") + ");";
    }

    static String textScript(String text) {
        return TEXT_BODY + "(" + quote(text) + ");";
    }

    // GLFW modifier bits; duplicated as literals so this class stays free of LWJGL.
    private static final int MOD_SHIFT = 0x1;
    private static final int MOD_CONTROL = 0x2;
    private static final int MOD_ALT = 0x4;
    private static final int MOD_SUPER = 0x8;

    private static String bool(int modifiers, int bit) {
        return (modifiers & bit) != 0 ? "true" : "false";
    }

    /** Wraps {@code value} in a single-quoted JavaScript string literal. */
    private static String quote(String value) {
        if (value == null) {
            return "''";
        }
        StringBuilder builder = new StringBuilder(value.length() + 8).append('\'');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '\'' -> builder.append("\\'");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\u2028' -> builder.append("\\u2028");
                case '\u2029' -> builder.append("\\u2029");
                case '<' -> builder.append("\\x3c");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.append('\'').toString();
    }
}
