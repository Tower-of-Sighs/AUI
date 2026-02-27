```html
<style>
    .stage {
        position: relative;
        width: 100%;
        height: 220px;
        overflow: hidden;

        background-color: #1b1f28;
        border: 1px solid #000;
        border-radius: 8px;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);

        padding: 12px;
    }

    .title {
        color: #f0f0f0;
        font-size: 16px;
        line-height: 1.5;
        text-align: left;
        margin: 0 0 10px 0;
    }

    .bear {
        position: absolute;
        width: 200px;
        height: 100px;

        left: 0;
        top: 90px;

        background-image: url("bear.png");
        background-repeat: no-repeat;
        background-position: 0 0;

        transform: translateX(0);
        animation: bear_run 1s steps(8) infinite, bear_move 4s infinite;
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
        0%   {
            transform: translateX(0);
        }
        100% {
            transform: translateX(400px);
        }
    }

    .bucket {
        position: absolute;
        width: 48px;
        height: 48px;

        left: 60%;
        top: 28px;
        z-index: 10;

        background-image: url("lava_bucket.png");
        background-repeat: no-repeat;
        background-position: 0 0;

        transform: translateX(0);
        animation: bucket_cycle 1.2s steps(24) infinite, bucket_float 1.1s infinite;
    }

    @keyframes bucket_cycle {
        0% {
            background-position: 0 0;
        }
        100% {
            background-position: -1152px 0;
        }
    }

    @keyframes bucket_float {
        0% {
            transform: translateY(0) scale(1);
        }
        50% {
            transform: translateY(-6px) scale(1.06);
        }
        100% {
            transform: translateY(0) scale(1);
        }
    }
</style>

<body>
<div class="stage">
    <div class="title">ApricityUI Showcase — Sprite steps() + @keyframes</div>

    <div class="bucket"></div>
    <div class="bear"></div>
</div>
</body>
```