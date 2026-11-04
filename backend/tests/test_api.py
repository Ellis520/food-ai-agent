from unittest.mock import AsyncMock, MagicMock
import pytest
from fastapi.testclient import TestClient
from app import main

@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv('FOODMOMENT_DB',str(tmp_path/'test.sqlite'))
    for name in ['DASHSCOPE_API_KEY','AMAP_WEB_SERVICE_KEY','FUNASR_WS_URL']:
        monkeypatch.delenv(name,raising=False)
    with TestClient(main.app) as client:
        client.get('/api/state')
        yield client

def test_sqlite_persistence_and_conflict(client):
    original=client.get('/api/state').json()['data']
    original['state']['profile']['name']='测试同学'
    result=client.put('/api/state',json=original)
    assert result.status_code==200
    assert client.get('/api/state').json()['data']['state']['profile']['name']=='测试同学'
    assert client.put('/api/state',json=original).status_code==409

def test_anonymous_session_isolation(client):
    state=client.get('/api/state').json()['data'];state['state']['profile']['name']='私有记录'
    assert client.put('/api/state',json=state).status_code==200
    with TestClient(main.app) as other:
        assert other.get('/api/state').json()['data']['state']['profile']['name']!='私有记录'

def test_validation(client):
    state=client.get('/api/state').json()['data']
    state['state']['meals']=[{'id':'bad','date':'2026-11-02','foodId':'invented','slot':'午餐','grams':-5,'cost':10}]
    assert client.put('/api/state',json=state).status_code==422

def test_plan_record_persistence_edit_and_duplicate_rejection(client):
    state=client.get('/api/state').json()['data']
    meal={'id':'planned-breakfast','date':'2026-11-02','slot':'早餐','foodId':'oats','grams':220,'cost':7.5,'planRef':'2026-11-02:早餐'}
    state['state']['meals']=[meal]
    assert client.put('/api/state',json=state).status_code==200
    state=client.get('/api/state').json()['data']
    assert state['state']['meals'][0]['planRef']==meal['planRef']
    state['state']['meals'][0].update(grams=180,cost=6)
    assert client.put('/api/state',json=state).status_code==200
    state=client.get('/api/state').json()['data']
    state['state']['meals'].append({**meal,'id':'duplicate'})
    assert client.put('/api/state',json=state).status_code==422
    stored=client.get('/api/state').json()['data']
    assert len(stored['state']['meals'])==1 and stored['state']['meals'][0]['grams']==180
    stored['state']['meals']=[]
    assert client.put('/api/state',json=stored).status_code==200
    state=client.get('/api/state').json()['data'];state['state']['meals']=[meal]
    assert client.put('/api/state',json=state).status_code==200

@pytest.mark.parametrize('reference',['2026-11-03:早餐','2026-11-02:午餐','invalid-reference'])
def test_plan_record_reference_must_match_record(client,reference):
    state=client.get('/api/state').json()['data']
    state['state']['meals']=[{'id':'mismatch','date':'2026-11-02','slot':'早餐','foodId':'oats','grams':220,'cost':8,'planRef':reference}]
    assert client.put('/api/state',json=state).status_code==422
    assert client.get('/api/state').json()['data']['state']['meals']==[]

def test_unconfigured_providers_are_explicit(client):
    assert client.post('/api/chat',json={'message':'你好','history':[]}).status_code==503
    assert client.get('/api/food/nearby').status_code==503
    assert client.post('/api/language/translate',json={'text':'你好','target':'en'}).status_code==503

def test_budget_validation(client,monkeypatch):
    assert client.post('/api/diet/plan',json={'budget':20,'preference':'不限','day':0}).status_code==422
    import json
    items=[{'slot':'早餐','foodId':'oats','grams':250},{'slot':'午餐','foodId':'chicken','grams':350},{'slot':'晚餐','foodId':'salad','grams':300}]
    monkeypatch.setattr(main,'qwen_turn',AsyncMock(return_value={'content':json.dumps({'items':items})}))
    result=client.post('/api/diet/plan',json={'budget':45,'preference':'不限','day':0})
    assert result.status_code==200 and len(result.json()['data'])==3
    assert result.json()['source']=='qwen-sample-catalog'
    assert client.get('/api/state').json()['data']['state']['plans']=={}

@pytest.mark.parametrize('case',['unknown_food','oversized','wrong_slot','over_budget','vegetarian_meat','invalid_json'])
def test_ai_plan_invalid_outputs_are_not_saved(client,monkeypatch,case):
    import json
    items=[{'slot':'早餐','foodId':'oats','grams':250},{'slot':'午餐','foodId':'chicken','grams':350},{'slot':'晚餐','foodId':'salad','grams':300}]
    budget=45;preference='不限'
    if case=='unknown_food':items[0]['foodId']='invented-food'
    if case=='oversized':items[1]['grams']=2001
    if case=='wrong_slot':items[0]['foodId']='chicken'
    if case=='over_budget':budget=30
    if case=='vegetarian_meat':preference='素食'
    content='not json' if case=='invalid_json' else json.dumps({'items':items})
    model=AsyncMock(return_value={'content':content})
    monkeypatch.setattr(main,'qwen_turn',model)
    assert client.post('/api/diet/plan',json={'budget':budget,'preference':preference,'day':0}).status_code==502
    assert model.await_count==2
    assert client.get('/api/state').json()['data']['state']['plans']=={}

def test_qwen_response_and_no_duplicate_history(client,monkeypatch):
    monkeypatch.setenv('DASHSCOPE_API_KEY','test-key-never-real')
    upstream=MagicMock();upstream.status_code=200;upstream.json.return_value={'choices':[{'message':{'content':'真实调用适配测试'}}]}
    http=AsyncMock();http.post.return_value=upstream
    manager=AsyncMock();manager.__aenter__.return_value=http
    monkeypatch.setattr(main.httpx,'AsyncClient',lambda **kw:manager)
    result=client.post('/api/chat',json={'message':'午餐','history':[{'id':'1','role':'user','text':'午餐'}]})
    assert result.status_code==200 and result.json()['source']=='qwen'
    assert len(http.post.call_args.kwargs['json']['messages'])==2


def test_chat_budget_clarification_and_follow_up(client, monkeypatch):
    model = AsyncMock(return_value={'content': '按您这餐 20 元的预算，可以参考一份番茄鸡蛋面。'})
    monkeypatch.setattr(main, 'qwen_turn', model)
    initial = {'id': 'user1', 'role': 'user', 'text': '预算20元，怎么吃？'}
    reply = client.post('/api/chat', json={'message': initial['text']}).json()['data']
    assert '一顿饭' in reply['text'] and '一天三餐' in reply['text'] and '您' in reply['text']
    model.assert_not_awaited()

    response = client.post('/api/chat', json={'message': '午饭', 'history': [initial, reply]})
    assert response.status_code == 200 and 'kind' not in response.json()['data']
    turns = model.await_args.args[0]
    assert turns[1]['content'] == initial['text']
    assert '单餐' in turns[0]['content'] and '"午餐": ["快餐", "粉面"' in turns[0]['content']
    assert not any(tool['function']['name'] == 'generate_diet_plan' for tool in model.await_args.kwargs['tools'])


@pytest.mark.parametrize(('message', 'plan_tool'), [
    ('预算 20 元，午餐怎么吃？', False),
    ('午饭预算二十块', False),
    ('一天预算60元，晚饭怎么吃', False),
    ('一天预算60元，请安排三餐', True),
])
def test_chat_chooses_day_plan_tool_only_for_day_requests(client, monkeypatch, message, plan_tool):
    model = AsyncMock(return_value={'content': '我来帮您搭配。'})
    monkeypatch.setattr(main, 'qwen_turn', model)
    assert client.post('/api/chat', json={'message': message}).status_code == 200
    assert any(tool['function']['name'] == 'generate_diet_plan' for tool in model.await_args.kwargs['tools']) == plan_tool


def test_chat_recommendations_use_planning_options_and_can_be_saved(client, monkeypatch):
    import json

    meal = {'slot':'午餐','option':'快餐'}
    def tool_call(*choices):
        return {'tool_calls':[{'id':'recommend','function':{
            'name':'recommend_meals','arguments':json.dumps({'meals':choices})
        }}]}
    model = AsyncMock(side_effect=[
        tool_call({**meal,'option':'香煎鸡肉杂粮饭'}),
        tool_call({**meal,'name':'清蒸鱼配米饭'}),
        tool_call(meal, meal),
    ])
    monkeypatch.setattr(main, 'qwen_turn', model)
    response = client.post('/api/chat', json={'message':'预算20元，午餐怎么吃？'})
    assert response.status_code == 200
    message = response.json()['data']
    canonical = {**meal,'name':'快餐','detail':''}
    assert message['mealSuggestions'] == [canonical]
    assert model.await_count == 3
    assert any(turn['role'] == 'tool' and '工具参数无效' in turn['content'] for turn in model.await_args.args[0])

    state = client.get('/api/state').json()['data']
    state['state']['messages'] = [message, {
        'id':'selected','role':'assistant','text':'需要帮您找店吗？','selectedMeal':canonical,
    }]
    assert client.put('/api/state', json=state).status_code == 200
    assert client.get('/api/state').json()['data']['state']['messages'][1]['selectedMeal'] == canonical


def test_profile_avatar_can_be_saved_and_cleared(client):
    from base64 import b64encode
    from io import BytesIO
    from PIL import Image

    state = client.get('/api/state').json()['data']
    with BytesIO() as photo:
        Image.new('RGB', (12, 12), 'green').save(photo, format='JPEG')
        avatar = 'data:image/jpeg;base64,' + b64encode(photo.getvalue()).decode()
    state['state']['profile']['avatar'] = 'https://example.invalid/profile.jpg'
    assert client.put('/api/state', json=state).status_code == 422
    state['state']['profile']['avatar'] = avatar
    assert client.put('/api/state', json=state).status_code == 200
    state = client.get('/api/state').json()['data']
    assert state['state']['profile']['avatar'] == avatar
    state['state']['profile']['avatar'] = None
    assert client.put('/api/state', json=state).status_code == 200
    assert client.get('/api/state').json()['data']['state']['profile'].get('avatar') is None


@pytest.mark.parametrize(('prompt','expected'), [
    ('帮我找一家椰子鸡店', ['branch', 'close', 'unrated']),
    ('帮我找离我最近的椰子鸡店', ['unrated', 'close', 'branch']),
])
def test_chat_returns_real_poi_choices_and_preserves_them_in_history(client, monkeypatch, prompt, expected):
    base = {'address': '新港西路', 'hours': '10:00–21:00', 'distance': 805,
            'lng': 113.30, 'lat': 23.09, 'source': 'amap'}
    shops = [
        {**base, 'id': 'irrelevant', 'name': '茶饮店', 'rating': 5},
        {**base, 'id': 'unrated', 'name': '椰子鸡（东门店）', 'distance': 100},
        {**base, 'id': 'close', 'name': '椰子鸡（南门店）', 'rating': 4.0, 'distance': 200},
        {**base, 'id': 'branch', 'name': '椰子鸡（中大店）', 'price': 63, 'rating': 4.3,
         'photoUrl': 'https://example.invalid/image.jpg'},
    ]
    nearby = AsyncMock(return_value=main.envelope(shops, 'amap'))
    monkeypatch.setattr(main, 'nearby', nearby)
    monkeypatch.setattr(main, 'qwen_turn', AsyncMock(side_effect=[
        {'content': None, 'tool_calls': [{
            'id': 'lookup', 'type': 'function',
            'function': {'name': 'search_campus_restaurants', 'arguments': '{"keyword":"椰子鸡"}'},
        }]},
        {'content': '找到校园附近的椰子鸡店。'},
    ]))
    response = client.post('/api/chat', json={'message': prompt})
    assert response.status_code == 200
    message = response.json()['data']
    search = message['restaurantSearch']
    assert search['keyword'] == '椰子鸡'
    assert [place['id'] for place in search['places']] == expected
    assert search['places'][0]['lng'] == 113.30
    assert 'photoUrl' not in search['places'][0]
    assert nearby.await_args.kwargs['keyword'] == '椰子鸡'

    state = client.get('/api/state').json()['data']
    state['state']['messages'] = [message, {**message, 'id': 'follow-up', 'restaurantMode': 'dine-in'}]
    assert client.put('/api/state', json=state).status_code == 200
    stored = client.get('/api/state').json()['data']['state']['messages']
    assert stored[1]['restaurantMode'] == 'dine-in'
    assert [place['id'] for place in stored[1]['restaurantSearch']['places']] == expected


def test_map_search_passes_keyword_radius_and_page_to_amap(client, monkeypatch):
    import httpx
    from services.map_service import _GEOCODE_CACHE

    _GEOCODE_CACHE.clear()
    monkeypatch.setenv('AMAP_WEB_SERVICE_KEY', 'test-key')
    monkeypatch.setenv('CAMPUS_LOCATION', '113.300,23.090')

    def amap(request):
        if request.url.path.endswith('/geocode/geo'):
            return httpx.Response(200, json={'status': '1', 'geocodes': [{'location': '113.300,23.090'}]})
        assert request.url.params['keywords'] == '麦当劳'
        assert request.url.params['location'] == '113.300000,23.090000'
        assert request.url.params['radius'] == '5000'
        assert request.url.params['page'] == '2'
        assert request.url.params['types'] == '050000'
        return httpx.Response(200, json={'status': '1', 'pois': [
            {'id': 'branch-21', 'name': '麦当劳（测试分店）', 'location': '113.31,23.09'},
        ]})

    original_client = httpx.AsyncClient
    monkeypatch.setattr(main.httpx, 'AsyncClient', lambda **kwargs: original_client(
        transport=httpx.MockTransport(amap), **kwargs
    ))
    response = client.get('/api/food/search', params={'keyword': '麦当劳', 'radius': 5000, 'page': 2})
    assert response.status_code == 200
    assert response.json()['data']['restaurants'][0]['id'] == 'branch-21'
    assert response.json()['data']['hasMore'] is False


def test_chat_restaurant_cards_accept_eight_real_places():
    places = [{'id': str(i), 'name': f'麦当劳（分店{i}）', 'address': '', 'hours': '',
               'lng': 113.31, 'lat': 23.09, 'source': 'amap'} for i in range(12)]
    search = main.chat_restaurant_search(main.envelope(places, 'amap'), '麦当劳')
    assert len(search.places) == 8
    assert main.Message(id='test', role='assistant', text='推荐', restaurantSearch=search)


@pytest.mark.parametrize(('query','status','expected'), [
    ('我身体不舒服', 'clarify', '主要症状'),
    ('今天吃完饭后发现我身体不太舒服', 'clarify', '饭后立刻出现'),
    ('呼吸有点困难，帮我找药店', 'urgent', '120'),
    ('我22岁，今天有点发烧，体温38.2℃，想问吃什么药', 'otc', '对乙酰氨基酚'),
    ('我22岁，今天有点发烧，体温39.8℃，想问吃什么药', 'clinic', '医院'),
    ('我22岁，今天有点发烧，体温38.2℃，有肝病，吃什么药', 'assessment', '药师'),
    ('我23岁，今天有点拉肚子', 'hydration', '口服补液盐'),
])
def test_health_guidance_stays_local(client, monkeypatch, query, status, expected):
    model = AsyncMock(side_effect=AssertionError('Do not send health details to Qwen'))
    monkeypatch.setattr(main, 'qwen_turn', model)
    lookup = AsyncMock(side_effect=AssertionError('Only look up places when asked and appropriate'))
    monkeypatch.setattr(main, 'search_nearby', lookup)
    result = client.post('/api/chat', json={'message': query})
    assert result.status_code == 200
    assert result.json()['source'] == 'local-health'
    message = result.json()['data']
    assert message['healthStatus'] == status
    assert expected in message['text']
    if '有肝病' in query:
        assert '对乙酰氨基酚' not in message['text']
    assert 'restaurantSearch' not in message
    assert model.await_count == 0 and lookup.await_count == 0


def test_health_followup_after_clarifying_question(client, monkeypatch):
    model = AsyncMock(side_effect=AssertionError('Health follow-up must stay local'))
    monkeypatch.setattr(main, 'qwen_turn', model)
    initial = client.post('/api/chat', json={'message': '我身体不舒服'}).json()['data']
    symptoms = '我年龄十八岁然后感觉口干舌燥肚子痛然后腹泻'
    history = [initial, {'id': 'symptoms', 'role': 'user', 'text': symptoms}]
    response = client.post('/api/chat', json={'message': symptoms, 'history': history})
    assert response.status_code == 200
    result = response.json()['data']
    assert result['healthStatus'] == 'assessment'
    assert result['healthTopic'] == 'diarrhea'
    assert '水分流失' in result['text'] and '请问您现在能正常喝水吗' in result['text']
    assert model.await_count == 0


def test_headache_assessment_uses_previous_answers_and_prioritizes_urgent_signs(client):
    first = client.post('/api/chat', json={'message': '头痛'}).json()['data']
    assert first['healthTopic'] == 'headache'
    assert '初步判断' in first['text'] and '什么时候开始' in first['text']
    history = [{'id': 'headache', 'role': 'user', 'text': '头痛'}, first]
    second = client.post('/api/chat', json={'message': '今天上午慢慢开始的，四分', 'history': history}).json()['data']
    assert second['healthStatus'] == 'assessment'
    assert '哪个部位疼' in second['text'] and '什么时候开始' not in second['text']
    urgent = client.post('/api/chat', json={'message': '是突然开始的，疼得受不了', 'history': history}).json()['data']
    assert urgent['healthStatus'] == 'urgent'
    assert '120' in urgent['text']
    continued = client.post('/api/chat', json={'message': '刚才开始的', 'history': [urgent]}).json()['data']
    assert continued['healthStatus'] == 'urgent'


def test_health_search_uses_map_types_and_converted_location(client, monkeypatch):
    import httpx
    from services.map_service import _GEOCODE_CACHE

    _GEOCODE_CACHE.clear()
    monkeypatch.setenv('AMAP_WEB_SERVICE_KEY', 'test-key')
    monkeypatch.setenv('CAMPUS_LOCATION', '113.300,23.090')
    calls = []

    def highde(request):
        calls.append(request)
        if request.url.path.endswith('/geocode/geo'):
            return httpx.Response(200, json={'status': '1', 'geocodes': [{'location': '113.300,23.090'}]})
        if request.url.path.endswith('/convert'):
            assert request.url.params['coordsys'] == 'gps'
            assert request.url.params['locations'] == '113.302000,23.091000'
            return httpx.Response(200, json={'status': '1', 'locations': '113.307,23.089'})
        code = request.url.params['types']
        label = '测试综合医院' if code == '090100' else '测试药店'
        return httpx.Response(200, json={'status': '1', 'pois': [
            {'id': 'wrong', 'name': '宠物诊所', 'location': '113.31,23.09', 'typecode': '090701'},
            {'id': 'correct', 'name': label, 'location': '113.31,23.09',
             'typecode': '090101' if code == '090100' else '090601', 'distance': '400'},
        ]})

    original_client = httpx.AsyncClient
    monkeypatch.setattr(main.httpx, 'AsyncClient', lambda **kwargs: original_client(
        transport=httpx.MockTransport(highde), **kwargs
    ))
    hospital = client.post('/api/chat', json={'message': '帮我找附近的医院'}).json()['data']
    assert hospital['restaurantSearch']['category'] == 'hospital'
    assert [place['name'] for place in hospital['restaurantSearch']['places']] == ['测试综合医院']
    around = [call for call in calls if call.url.path.endswith('/around')]
    assert around[0].url.params['location'] == '113.300000,23.090000'
    pharmacy = client.post('/api/chat', json={'message': '查附近药店', 'healthLookup': 'pharmacy',
                                             'healthGPS': {'lng': 113.302, 'lat': 23.091}}).json()['data']
    assert pharmacy['restaurantSearch']['category'] == 'pharmacy'
    assert pharmacy['restaurantSearch']['origin'] == 'location'
    assert [place['name'] for place in pharmacy['restaurantSearch']['places']] == ['测试药店']
    around = [call for call in calls if call.url.path.endswith('/around')]
    assert around[-1].url.params['location'] == '113.307000,23.089000'
    assert 'healthGPS' not in pharmacy

def test_profile_campus_moves_the_nearby_search_center(client, monkeypatch):
    import httpx
    from services.map_service import _GEOCODE_CACHE

    _GEOCODE_CACHE.clear()
    monkeypatch.setenv('AMAP_WEB_SERVICE_KEY', 'test-key')
    monkeypatch.setenv('CAMPUS_LOCATION', '113.298214,23.096746')
    state = client.get('/api/state').json()['data']
    state['state']['profile']['campus'] = '中山大学东校区'
    assert client.put('/api/state', json=state).status_code == 200
    seen = {}

    def amap(request):
        if request.url.path.endswith('/geocode/geo'):
            assert request.url.params['address'] == '广州市番禺区大学城外环东路132号中山大学东校区'
            assert request.url.params['city'] == '广州'
            return httpx.Response(200, json={'status': '1', 'geocodes': [{'location': '113.390,23.060'}]})
        seen['location'] = request.url.params['location']
        return httpx.Response(200, json={'status': '1', 'pois': [
            {'id': 'east-1', 'name': '大学城食堂', 'location': '113.391,23.061', 'distance': '120'},
        ]})

    original_client = httpx.AsyncClient
    monkeypatch.setattr(main.httpx, 'AsyncClient', lambda **kwargs: original_client(
        transport=httpx.MockTransport(amap), **kwargs
    ))
    response = client.get('/api/food/search', params={'keyword': '食堂', 'radius': 5000, 'page': 1})
    assert response.status_code == 200
    assert seen['location'] == '113.390000,23.060000'
    assert response.json()['data']['restaurants'][0]['name'] == '大学城食堂'
    state['state']['profile']['campus'] = '其他校园'
    assert client.put('/api/state', json=state).status_code == 422


def test_poi_unknown_metadata_remains_null(client,monkeypatch):
    from services.map_service import _GEOCODE_CACHE

    _GEOCODE_CACHE.clear()
    monkeypatch.setenv('AMAP_WEB_SERVICE_KEY','test-key');monkeypatch.setenv('CAMPUS_LOCATION','113.3,23.1')
    upstream=MagicMock();upstream.status_code=200;upstream.json.return_value={'status':'1','pois':[{'id':'test','name':'接口测试餐厅','location':'113.3,23.1','biz_ext':{},'distance':'100','photos':[{'url':'javascript:bad'},{'url':'http://store.is.autonavi.com/sample.jpg'}]}]}
    geocoded=MagicMock();geocoded.status_code=200;geocoded.json.return_value={'status':'1','geocodes':[{'location':'113.300,23.100'}]}
    http=AsyncMock();http.get.side_effect=lambda url, **kwargs: geocoded if 'geocode/geo' in str(url) else upstream
    manager=AsyncMock();manager.__aenter__.return_value=http
    monkeypatch.setattr(main.httpx,'AsyncClient',lambda **kw:manager)
    result=client.get('/api/food/nearby').json()['data'][0]
    assert result['price'] is None and result['rating'] is None and result['source']=='amap'
    assert result['photoUrl']=='https://store.is.autonavi.com/sample.jpg'

def test_asr_missing_configuration(client):
    with client.websocket_connect('/api/asr/stream',headers={'origin':'http://testserver'}) as socket:
        assert '未配置' in socket.receive_json()['error']

def test_agent_reads_only_current_session(client,monkeypatch):
    turns=AsyncMock(side_effect=[{'tool_calls':[{'id':'call-1','type':'function','function':{'name':'get_diet_summary','arguments':'{}'}}]}, {'content':'当前没有饮食记录。'}])
    monkeypatch.setattr(main,'qwen_turn',turns)
    result=client.post('/api/chat',json={'message':'这周吃了什么？','history':[]})
    assert result.status_code==200
    assert '业务工具' in result.json()['data']['source']
    sent=turns.call_args.args[0][-1]
    import json
    assert json.loads(sent['content'])['data']['records']==[]

def test_agent_rejects_unregistered_tools(client,monkeypatch):
    turns=AsyncMock(side_effect=[{'tool_calls':[{'id':'call-1','type':'function','function':{'name':'execute_shell','arguments':'{}'}}]}, {'content':'无法执行此操作。'}])
    monkeypatch.setattr(main,'qwen_turn',turns)
    assert client.post('/api/chat',json={'message':'测试','history':[]}).status_code==200
    assert '工具参数无效' in turns.call_args.args[0][-1]['content']

def test_amap_security_proxy_keeps_security_code_server_side(client,monkeypatch):
    monkeypatch.setenv('AMAP_SECURITY_CODE','test-server-only-code')
    upstream=MagicMock();upstream.status_code=200;upstream.content=b'{"ok":true}';upstream.headers={'content-type':'application/json'}
    http=AsyncMock();http.get.return_value=upstream
    manager=AsyncMock();manager.__aenter__.return_value=http
    monkeypatch.setattr(main.httpx,'AsyncClient',lambda **kw:manager)
    result=client.get('/api/amap/_AMapService/v3/config?key=public-js-key')
    assert result.status_code==200
    assert http.get.call_args.kwargs['params']['jscode']=='test-server-only-code'
    assert 'test-server-only-code' not in result.text
    assert client.get('/api/amap/_AMapService/unknown/path').status_code==400

def test_asr_proxy_forwards_pcm_and_returns_final_text(client,monkeypatch):
    import asyncio
    import json
    from contextlib import asynccontextmanager
    monkeypatch.setenv('FUNASR_WS_URL','ws://127.0.0.1:10095')
    class Upstream:
        def __init__(self):self.sent=[];self.stopped=asyncio.Event()
        async def send(self,data):
            self.sent.append(data)
            if isinstance(data,str) and json.loads(data).get('is_speaking') is False:self.stopped.set()
        def __aiter__(self):return self.results()
        async def results(self):
            await asyncio.wait_for(self.stopped.wait(),2)
            yield json.dumps({'mode':'2pass-offline','text':'测试校正结果','is_final':True})
    upstream=Upstream()
    @asynccontextmanager
    async def fake_connect(*args,**kwargs):yield upstream
    monkeypatch.setattr(main.websockets,'connect',fake_connect)
    with client.websocket_connect('/api/asr/stream',headers={'origin':'http://testserver'}) as socket:
        socket.send_bytes(b'\x00\x00\x01\x00')
        socket.send_json({'stop':True})
        final=socket.receive_json()
        assert final['text']=='测试校正结果' and final['mode']=='2pass-offline'
    assert json.loads(upstream.sent[0])['audio_fs']==16000
    assert b'\x00\x00\x01\x00' in upstream.sent

def test_favorite_places_resolve_saved_poi(client, monkeypatch):
    monkeypatch.setenv('AMAP_WEB_SERVICE_KEY', 'test-key')
    monkeypatch.setenv('CAMPUS_LOCATION', '113.3,23.1')
    state = client.get('/api/state').json()['data']
    state['state']['favorites'] = ['B0FFFSW069', 'not-an-id']
    assert client.put('/api/state', json=state).status_code == 200
    upstream = MagicMock()
    upstream.status_code = 200
    upstream.json.return_value = {'status': '1', 'pois': [{
        'id': 'B0FFFSW069',
        'name': '尚膳御汤·脆肉鲩焖扇鸡(江怡路店)',
        'location': '113.301,23.101',
        'address': '江怡路104号',
        'biz_ext': {'cost': '84', 'rating': '4.6', 'open_time': '10:30-23:00'},
        'photos': [{'url': 'https://store.is.autonavi.com/sample.jpg'}],
    }]}
    http = AsyncMock()
    http.get.return_value = upstream
    manager = AsyncMock()
    manager.__aenter__.return_value = http
    monkeypatch.setattr(main.httpx, 'AsyncClient', lambda **kw: manager)
    result = client.get('/api/food/favorites')
    assert result.status_code == 200
    body = result.json()['data']
    assert body['missing'] == 1
    assert body['restaurants'][0]['name'] == '尚膳御汤·脆肉鲩焖扇鸡(江怡路店)'
    assert body['restaurants'][0]['photoUrl'] == 'https://store.is.autonavi.com/sample.jpg'
    assert body['restaurants'][0]['price'] == 84
    assert http.get.call_args.args[0].endswith('/place/detail')
    assert http.get.call_args.kwargs['params']['id'] == 'B0FFFSW069'

def test_favorite_places_skip_lookup_when_empty(client):
    result = client.get('/api/food/favorites')
    assert result.status_code == 200
    assert result.json()['data'] == {'restaurants': [], 'missing': 0}
