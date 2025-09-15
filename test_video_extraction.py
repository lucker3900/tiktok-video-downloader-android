#!/usr/bin/env python3
import requests
import json
import re

def test_video_extraction():
    """测试视频URL提取"""
    
    # 测试链接
    share_url = "https://v.douyin.com/N2IGGtgqs94/"
    video_id = "7524651553081380105"
    
    print(f"测试链接: {share_url}")
    print(f"视频ID: {video_id}")
    print("-" * 50)
    
    # 1. 测试重定向
    print("1. 测试重定向...")
    try:
        response = requests.get(share_url, allow_redirects=True, timeout=10)
        print(f"   最终URL: {response.url}")
        print(f"   状态码: {response.status_code}")
    except Exception as e:
        print(f"   重定向失败: {e}")
    
    # 2. 测试直接构造的视频URL
    print("\n2. 测试直接构造的视频URL...")
    base_urls = [
        "https://aweme-hl.snssdk.com/aweme/v1/play/",
        "https://aweme-hl.snssdk.com/aweme/v1/playwm/",
    ]
    
    for base_url in base_urls:
        for ratio in ["720p", "540p", "480p", "360p"]:
            test_url = f"{base_url}?video_id={video_id}&line=0&ratio={ratio}&media=mp4&vr_type=0&improve_bitrate=0&is_play_url=1&source=PackSourceEnum_PUBLISH"
            try:
                response = requests.head(test_url, timeout=5)
                print(f"   {ratio}: {response.status_code} - {len(response.content)} bytes")
                if response.status_code == 200 and len(response.content) > 0:
                    print(f"   ✅ 找到可用URL: {test_url}")
                    return test_url
            except Exception as e:
                print(f"   {ratio}: 失败 - {e}")
    
    print("\n❌ 所有方法都失败了")
    return None

if __name__ == "__main__":
    result = test_video_extraction()
    if result:
        print(f"\n✅ 成功获取视频URL: {result}")
    else:
        print("\n❌ 无法获取视频URL")
