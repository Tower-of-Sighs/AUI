---
title: Implementing Player Message Bubbles With AUI
description: This example uses AUI's WorldWindow class and listens to ClientChatReceivedEvent to show a message bubble above the player's head. It is intended as a reference only.
last_update:
  date: 3/15/2026
  author:
---

# Implementing Player Message Bubbles With AUI

This page is the showcase version of the same player-message example. For the full Java example, see [../guide/example.md](../guide/example.md).

`assets/apricityui/apricity/player_message.html`

```html
<body>
<div id="message-container" class="bubble">
    <div id="message-lines" class="bubble-txt">
        <span id="message-measure" class="bubble-txt" style="display:none"></span>
        <span class="bubble-txt">Meow~Meow~Meow~</span>
    </div>
</div>
</body>

<link rel="stylesheet" href="global.css">

<style>
    body {
        margin: 0;
        padding: 0;
        overflow: visible;
        display: flex;
        justify-content: center;
        align-items: center;
    }

    .bubble {
        width: fit-content;
        border: 9px solid transparent;
        border-image: url('https://f.loli.ly/snowflake_cat.png') 34 69 52 88 fill / 8.5px 17.25px 13px 22px stretch;
        color: #fff;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        line-height: 9px;
        transition: opacity 0.1s linear;
    }

    .bubble-txt {
        color: inherit;
        font-size: 16px;
        padding: 0 10px;
    }

    #message-lines {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0;
    }
</style>
```
