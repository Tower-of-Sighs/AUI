## 简单用例

```html
<body>
    <div class="options">
        <div class="head">
            <span style="color: #ad4234;font-size: 18px;font-family: title;width: 50px;">瑞雪点将录</span>
            <span style="color: #777;font-size: 12px;">更多</span>
        </div>
        <div class="area">
            <div class="option">
                <img src="387989_1741606149_lAky.png" />
                <div><span>元宇宙虚拟主播同好会</span><span class="detail">春江潮水连海平</span></div>
            </div>
            <div class="option">
                <img src="huixiaoye.jpg" />
                <div><span>辉小月</span><span class="detail">汀上白沙看不见</span></div>
            </div>
            <div class="option">
                <img src="zhenshiz.jpg" />
                <div><span>真实z</span><span class="detail">江畔何人初见月</span></div>
            </div>
            <div class="option">
                <img src="avatar.png" />
                <div><span>瑞雪真冬</span><span class="detail">江月何年初照人</span></div>
            </div>
        </div>
        <div class="footer que">
            <span>头衔：</span><input type="text" />
        </div>
        <div class="footer">
            <img id="selected" src="loading.gif" />
            <div class="btn">确定</div>
        </div>
        <div class="notice open">
            <div>
                <span>提交成功</span>
            </div>
        </div>
    </div>
</body>

<style src="test.css"></style>
<style>
    body>div {
        display: flex;
        position: relative;
        left: 0;
    }
    .notice {
        position: absolute;
        width: 90px;
        height: 55px;
        border-radius: 4px;
        background-color: #e97171;
        box-shadow: 0 0 4px #44000000;
        align-items: center;
        justify-content: center;
        top: 80px;
        transition: opacity 0.15s, transform 0.15s;
        transform: scale(1);
        z-index: -1;
    }
    .notice.close {
        opacity: 0;
        transform: scale(0.6);
    }
    .notice.open {
        animation-name: notice-enter;
        animation-duration: 0.3s;
        animation-timing-function: ease-out;
        animation-fill-mode: both;
        animation-play-state: running;
    }
    .notice>div {
        width: 80px;
        height: 45px;
        background-color: #fff;
        box-shadow: 0 0 2px #28000000;
        align-items: center;
        justify-content: center;
        font-family: lxgw;
        position: relative;
        background-image: url("2233.png");
        background-size: cover;
        border-image-source: url("border-diamonds.png");
        border-image-slice: 30;
        border-image-width: 4px;
    }
    .notice img {
        width: 28px;
        height: 16px;
        position: absolute;
        left: 5px;
        bottom: -13px;
    }
    .footer {
        flex-direction: row;
        margin: 10px;
    }
    .que {
        font-family: lxgw;
        font-size: 16px;
        color: #ad4234;
        margin-bottom: 0px;
        align-items: center;
    }
    input {
        color: #ad4234;
        border: unset;
        border-bottom: 1px solid #ad4234;
        background-color: unset;
        width: 50px;
        overflow: hidden;
        height: unset;
        font-size: 16px;
        padding: 2px;
    }
    .options {
        position: relative;
        background-color: #e9e9e9;
        width: 140px;
        align-items: center;
        padding-top: 3px;
        color: #ad4234;
        border: 1px solid #ad4234;
        box-shadow: 0 0 4px #44000000;
    }
    .area {
        width: 140px;
        align-items: center;
        height: 97px;
        overflow: hidden;
    }
    .head {
        width: 110px;
        border-bottom: 1px solid #ad4234;
        padding: 1px;
        margin: 3px;
        flex-direction: row;
        align-items: flex-end;
        justify-content: space-between;
    }
    .option {
        width: 110px;
        border-right: 2px solid #e97171;
        border-radius: 4px;
        background-color: #fff;
        box-shadow: 0 0 2px #22000000;
        margin: 2px;
        flex-direction: row;
        align-items: center;
        overflow: hidden;
        font-family: normal;
        transition: opacity 0.2s;
        font-size: 14px;
    }
    .option:hover {
        border-right: 2px solid #ad4234;
    }
    .option>img {
        width: 25px;
        height: 25px;
        border-radius: 0px;
        transition: border-radius 0.2s;
    }
    .option>div {
        height: 25px;
        justify-content: space-evenly;
        padding: 3px;
    }
    .detail {
        font-size: 10px;
        color: #777;
        font-family: remark;
    }
    .btn {
        color: #fff;
        background-color: #e97171;
        width: 36px;
        height: 15px;
        align-items: center;
        justify-content: center;
        margin: 8px;
        border-radius: 4px 0px 8px 2px;
        border: 1px solid #e97171;
        /*border-left: 2px solid #71b9e9;*/
        /*border-bottom: 3px solid #777777;*/
        /*border-right: 4px solid #a9ea84;*/
        transition: color 0.2s, background-color 0.2s, transform 0.1s;
        transform: scale(1);
        font-family: normal;
        /*filter: blur(20px);*/
    }
    .btn:hover {
        color: #e97171;
        background-color: #fff;
        box-shadow: 0 0 2px #22000000;
    }
    .btn:active {
        transform: scale(0.85);
    }
    #selected {
        width: 32px;
        height: 32px;
        border-radius: 16px;
        border: 1px solid #ad4234;
        box-shadow: 0 0 3px #22000000;
        transform: rotate(0);
        transition: transform 0.2s;
        animation: rotate 2s infinite alternate;
        animation-timing-function: ease-in-out;
    }
    @keyframes rotate {
        0% {
            transform: rotate(0);
        }
        100% {
            transform: rotate(360deg);
        }
    }
    @keyframes notice-enter {
        0% {
            opacity: 0;
            transform: scale(0.5);
        }
        70% {
            opacity: 1;
            transform: scale(1.2);
        }
        80% {
            opacity: 1;
            transform: scale(1.2);
        }
        100% {
            transform: scale(1.0);
        }
    }
</style>

<script>
    for (let element of document.querySelectorAll(".option")) {
        element.addEventListener("mousedown", function (event) {
            let img = event.currentTarget.querySelector("img").getAttribute("src");
            document.querySelector("#selected").setAttribute("src", img);
        });
    }
    document.querySelector(".btn").addEventListener("mousedown", e => {
        Window.setTimeout()
        new Timer().schedule(new TimerTask() {
            public void run() {
                document.querySelector(".notice").setAttribute("class", "notice open");
            }
        },400);
    });
</script>
```
