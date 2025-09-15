#!/usr/bin/env python3
import asyncio
import sys
import re
import os
from typing import List, Optional

from playwright.async_api import async_playwright, Browser, Page
import aiohttp

SHARE_URL = "https://v.douyin.com/N2IGGtgqs94/"
UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
)

CANDIDATE_PATTERNS = [
    r"/aweme/v1/play/",
    r"/aweme/v1/playwm/",
    r"\.mp4(\?|$)",
]

HEADERS = {
    "User-Agent": UA,
    "Accept": "*/*",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Cache-Control": "no-cache",
    "Pragma": "no-cache",
}

async def is_video_url(url: str) -> bool:
    return any(re.search(p, url) for p in CANDIDATE_PATTERNS)

async def pick_best(urls: List[str]) -> Optional[str]:
    # 优先无水印 play，其次 mp4，最后 playwm
    for key in ["/play/", ".mp4", "/playwm/"]:
        for u in urls:
            if key in u:
                return u
    return urls[0] if urls else None

async def download(url: str, out_path: str) -> bool:
    async with aiohttp.ClientSession(headers={**HEADERS, "Referer": "https://www.douyin.com/"}) as session:
        try:
            async with session.get(url, allow_redirects=True, timeout=60) as resp:
                if resp.status != 200:
                    return False
                os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
                with open(out_path, "wb") as f:
                    async for chunk in resp.content.iter_chunked(1 << 15):
                        if chunk:
                            f.write(chunk)
                return True
        except Exception:
            return False

async def main() -> None:
    target = SHARE_URL
    if len(sys.argv) > 1:
        target = sys.argv[1]

    found: List[str] = []

    async with async_playwright() as pw:
        browser: Browser = await pw.chromium.launch(headless=False, args=["--no-sandbox","--disable-blink-features=AutomationControlled","--disable-web-security"])
        context = await browser.new_context(
            user_agent=UA,
            extra_http_headers=HEADERS,
            ignore_https_errors=True,
        )
        page: Page = await context.new_page()

        page.on("response", lambda resp: None)

        async def on_response(resp):
            try:
                url = resp.url
                if await is_video_url(url):
                    # 取最终重定向后的URL
                    final_url = resp.url
                    if final_url not in found:
                        found.append(final_url)
            except Exception:
                pass

        page.on("response", on_response)

        # 跳到分享短链
        await page.goto(target, wait_until="load", timeout=90000)
        # 再等待页面主视频加载
        await page.wait_for_timeout(15000)

        # 如果跳转到 www.iesdouyin.com，再等一会儿
        for _ in range(3):
            await page.wait_for_timeout(7000)

        await context.storage_state(path="playwright_state.json")
        await page.wait_for_timeout(3000)
        await browser.close()

    best = await pick_best(found)
    if not best:
        print("未抓到直链")
        sys.exit(2)

    print(f"抓到候选直链: {best}")
    ok = await download(best, "douyin_video.mp4")
    if not ok:
        print("下载失败")
        sys.exit(3)

    size = os.path.getsize("douyin_video.mp4")
    if size <= 0:
        print("文件大小为0，下载无效")
        sys.exit(4)

    print(f"下载完成: douyin_video.mp4, 大小: {size} bytes")

if __name__ == "__main__":
    asyncio.run(main())
