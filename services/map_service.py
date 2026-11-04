"""Amap campus POI search and GPS conversion."""
import os
from urllib.parse import urlsplit, urlunsplit

import httpx
from fastapi import HTTPException

async def search_nearby(keyword, location, *, category='restaurant', gps=None, radius=None, page=1):
    key = os.getenv('AMAP_WEB_SERVICE_KEY')
    if not key:
        raise HTTPException(503,'高德未配置：请填写 AMAP_WEB_SERVICE_KEY。')
    if gps is not None:
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                converted = await client.get('https://restapi.amap.com/v3/assistant/coordinate/convert',
                                             params={'key': key, 'locations': f'{gps.lng:.6f},{gps.lat:.6f}',
                                                     'coordsys': 'gps'})
            data = converted.json()
            if converted.status_code != 200 or data.get('status') != '1' or not data.get('locations'):
                raise ValueError('coordinate conversion failed')
            location = data['locations']
        except (httpx.HTTPError, ValueError, TypeError, KeyError):
            raise HTTPException(502, '无法转换当前位置，可取消定位后按校园附近查找。')
    location = location or os.getenv('CAMPUS_LOCATION')
    if not location and os.getenv('CAMPUS_ADDRESS'):
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                geo = await client.get('https://restapi.amap.com/v3/geocode/geo',params={'key':key,'address':os.environ['CAMPUS_ADDRESS'],'city':'广州'})
                geodata = geo.json()
            if geodata.get('status')!='1' or not geodata.get('geocodes'):
                raise ValueError('no geocode')
            location = geodata['geocodes'][0]['location']
        except (httpx.HTTPError,ValueError,KeyError,TypeError):
            raise HTTPException(502,'校园地址定位失败，请检查高德权限或手动设置 CAMPUS_LOCATION。')
    try:
        lng,lat = map(float,(location or '').split(','))
        if not (73<=lng<=136 and 3<=lat<=54):
            raise ValueError()
    except ValueError:
        raise HTTPException(422,'请配置中国境内真实校园中心 CAMPUS_LOCATION（GCJ-02 经度,纬度）。')
    poi_type, default_radius = {'restaurant': ('050000', 2000), 'hospital': ('090100', 10000),
                                'pharmacy': ('090601', 5000)}[category]
    page_size = 20
    try:
        async with httpx.AsyncClient(timeout=15) as client:
            response = await client.get('https://restapi.amap.com/v3/place/around',params={'key':key,'location':location,'keywords':keyword[:60],'types':poi_type,'radius':radius or default_radius,'offset':page_size,'page':page,'extensions':'all','sortrule':'distance'})
            data = response.json()
        if response.status_code!=200 or data.get('status')!='1':
            raise HTTPException(502,'高德查询失败，请检查服务权限、配额及网络。')
        result = []
        def number(value):
            try: return float(value)
            except (TypeError,ValueError): return None
        pois = data.get('pois', [])
        for poi in pois:
            if category != 'restaurant':
                codes = str(poi.get('typecode') or '').split('|')
                accepted = {'090100', '090101', '090102'} if category == 'hospital' else {'090601'}
                if not accepted.intersection(codes):
                    continue
            coords = str(poi.get('location') or '').split(',')
            if len(coords)!=2: continue
            business = poi.get('biz_ext') or {}
            photo_url = None
            for photo in poi.get('photos') or []:
                if not isinstance(photo,dict): continue
                raw = photo.get('url')
                if not isinstance(raw,str): continue
                parsed = urlsplit(raw)
                if parsed.scheme not in {'http','https'} or not parsed.hostname or parsed.username or parsed.password: continue
                # Amap photo hosts support HTTPS; avoid mixed-content images on an HTTPS deployment.
                if parsed.scheme=='http' and (parsed.hostname.endswith('.autonavi.com') or parsed.hostname.endswith('.amap.com')):
                    raw = urlunsplit(parsed._replace(scheme='https'))
                if not raw.startswith('https://'): continue
                photo_url=raw;break
            result.append({'id':poi['id'],'name':poi['name'],'emoji':'🍽️','category':'餐饮','price':number(business.get('cost')),'rating':number(business.get('rating')),'distance':number(poi.get('distance')),'address':poi.get('address') or '地址未提供','hours':business.get('open_time') or '营业时间未提供','reason':'高德 POI 搜索结果；请以商家实际信息为准。','x':50,'y':50,'lng':float(coords[0]),'lat':float(coords[1]),'source':'amap','photoUrl':photo_url})
            if category == 'restaurant':
                for source, field in (('tel', 'phone'), ('type', 'cuisine'), ('tag', 'specialties')):
                    value = poi.get(source)
                    if isinstance(value, str) and value.strip():
                        result[-1][field] = value.strip()
        return {'data':result, 'source':'amap', 'hasMore':len(pois)>=page_size and page<10}
    except (httpx.HTTPError,ValueError,KeyError,TypeError):
        raise HTTPException(502,'高德返回异常或超时。')
