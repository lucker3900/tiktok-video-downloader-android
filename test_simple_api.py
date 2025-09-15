#!/usr/bin/env python3
import requests
import json

def test_simple_api():
    """测试简单的API调用"""
    
    video_id = "7524651553081380105"
    share_url = "https://v.douyin.com/N2IGGtgqs94/"
    
    print(f"测试视频ID: {video_id}")
    print(f"分享链接: {share_url}")
    print("-" * 50)
    
    # 尝试一个简单的API
    try:
        # 使用一个已知有效的API
        api_url = "https://api.tikwm.com/api"
        params = {
            "url": f"https://www.douyin.com/video/{video_id}",
            "hd": "1"
        }
        
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36",
            "Accept": "application/json, text/plain, */*",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8"
        }
        
        print(f"调用API: {api_url}")
        print(f"参数: {params}")
        
        response = requests.get(api_url, params=params, headers=headers, timeout=30)
        
        print(f"响应状态码: {response.status_code}")
        print(f"响应头: {dict(response.headers)}")
        print(f"响应内容长度: {len(response.text)}")
        
        if response.status_code == 200:
            try:
                data = response.json()
                print(f"JSON响应: {json.dumps(data, indent=2, ensure_ascii=False)}")
                
                # 尝试提取视频URL
                if "data" in data and "play" in data["data"]:
                    video_url = data["data"]["play"]
                    print(f"✅ 找到视频URL: {video_url}")
                    return video_url
                elif "data" in data and "hdplay" in data["data"]:
                    video_url = data["data"]["hdplay"]
                    print(f"✅ 找到高清视频URL: {video_url}")
                    return video_url
                else:
                    print("❌ 响应中没有找到视频URL")
            except json.JSONDecodeError:
                print(f"❌ 响应不是有效的JSON: {response.text[:500]}")
        else:
            print(f"❌ API调用失败: {response.status_code}")
            print(f"错误内容: {response.text[:500]}")
            
    except requests.exceptions.RequestException as e:
        print(f"❌ 请求异常: {e}")
    except Exception as e:
        print(f"❌ 其他异常: {e}")
    
    return None

if __name__ == "__main__":
    result = test_simple_api()
    if result:
        print(f"\n🎉 成功获取视频URL: {result}")
    else:
        print("\n😞 无法获取视频URL")
