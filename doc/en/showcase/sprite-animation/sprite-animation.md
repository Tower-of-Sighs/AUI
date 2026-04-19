```html
<style>
    .page {
        width: 100%;
        height: 300px;
        padding: 8px;
        background-color: #141821;
    }

    .title {
        color: #f0f0f0;
        font-size: 16px;
        line-height: 1.5;
        text-align: left;
        margin: 0 0 10px 0;
    }

    .group {
        margin: 0 0 12px 0;
    }

    .group-title {
        color: #d7dbe6;
        font-size: 14px;
        line-height: 1.4;
        text-align: left;
        margin: 0 0 8px 0;
    }

    .stage {
        position: relative;
        width: 600px;
        height: 160px;
        overflow: hidden;
        background-color: #1b1f28;
        border: 1px solid #000;
        border-radius: 8px;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
        padding: 12px;
    }

    .stage-1 {
        position: relative;
        width: 200px;
        height: 90px;
        overflow: hidden;
        background-color: #1b1f28;
        border: 1px solid #000;
        border-radius: 8px;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
        padding: 12px;
    }

    .bear {
        position: absolute;
        width: 200px;
        height: 100px;
        left: 0;
        top: 56px;
        background-image: url("bear.png");
        background-repeat: no-repeat;
        background-position: 0 0;
        transform: translateX(0);
        animation: bear_run 1s steps(8) infinite, bear_move 3.8s infinite;
    }

    @keyframes bear_run {
        0% {
            background-position: 0 0;
        }
        100% {
            background-position: -1600px 0;
        }
    }

    @keyframes bear_move {
        0% {
            transform: translateX(0);
        }
        100% {
            transform: translateX(400px);
        }
    }

    .bucket-sprite {
        position: absolute;
        width: 48px;
        height: 48px;
        left: 32px;
        top: 32px;
        z-index: 10;
    }
</style>

<body>
<div class="page">
    <div class="title">ApricityUI Showcase</div>

    <div class="stage-1">
        <div class="group-title">Group 2: `sprite` element (lava bucket, bucket_cycle only)</div>
        <sprite
                class="bucket-sprite"
                src="lava_bucket.png"
                steps="24"
                direction="right"
                duration="1.2s"
                loop="infinite"
                steps-mode="end"
                autoplay="true">
        </sprite>
    </div>

    <div class="stage">
        <div class="group-title">Group 1: hand-written `animation` + `@keyframes` (polar bear)</div>
        <div class="bear"></div>
    </div>
</div>
</body>
```
