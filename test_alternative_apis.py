#!/usr/bin/env python3
import requests
import json
import time

def test_alternative_apis():
    """测试替代API"""
    
    video_id = "7524651553081380105"
    share_url = "https://v.douyin.com/N2IGGtgqs94/"
    
    print(f"测试视频ID: {video_id}")
    print(f"分享链接: {share_url}")
    print("-" * 50)
    
    # 尝试多个不同的API
    apis = [
        {
            "name": "SSSTik",
            "url": "https://api.ssstik.com/abc",
            "params": {"url": f"https://www.douyin.com/video/{video_id}"}
        },
        {
            "name": "SnapTik",
            "url": "https://api.snap-tik.com/api",
            "params": {"url": f"https://www.douyin.com/video/{video_id}"}
        },
        {
            "name": "TikWM (HTTP)",
            "url": "http://api.tikwm.com/api",
            "params": {"url": f"https://www.douyin.com/video/{video_id}"}
        }
    ]
    
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36",
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8"
    }
    
    for api in apis:
        try:
            print(f"\n测试 {api['name']} API...")
            print(f"URL: {api['url']}")
            print(f"参数: {api['params']}")
            
            response = requests.get(
                api['url'], 
                params=api['params'], 
                headers=headers, 
                timeout=15,
                verify=False  # 忽略SSL验证
            )
            
            print(f"响应状态码: {response.status_code}")
            print(f"响应内容长度: {len(response.text)}")
            
            if response.status_code == 200 and response.text:
                try:
                    data = response.json()
                    print(f"JSON响应: {json.dumps(data, indent=2, ensure_ascii=False)[:500]}...")
                    
                    # 尝试提取视频URL
                    video_url = None
                    if "data" in data:
                        if "play" in data["data"]:
                            video_url = data["data"]["play"]
                        elif "hdplay" in data["data"]:
                            video_url = data["data"]["hdplay"]
                        elif "video_url" in data["data"]:
                            video_url = data["data"]["video_url"]
                    
                    if video_url:
                        print(f"✅ {api['name']} 成功获取视频URL: {video_url}")
                        return video_url
                    else:
                        print(f"❌ {api['name']} 响应中没有找到视频URL")
                        
                except json.JSONDecodeError:
                    print(f"❌ {api['name']} 响应不是有效的JSON: {response.text[:200]}...")
            else:
                print(f"❌ {api['name']} API调用失败: {response.status_code}")
                
        except requests.exceptions.RequestException as e:
            print(f"❌ {api['name']} 请求异常: {e}")
        except Exception as e:
            print(f"❌ {api['name']} 其他异常: {e}")
        
        time.sleep(1)  # 避免请求过快
    
    return None

if __name__ == "__main__":
    result = test_alternative_apis()
    if result:
        print(f"\n🎉 成功获取视频URL: {result}")
    else:
        print("\n😞 所有API都无法获取视频URL")
