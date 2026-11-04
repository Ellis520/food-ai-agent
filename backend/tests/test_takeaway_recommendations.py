import json
from urllib.parse import urlsplit,parse_qs
from unittest.mock import AsyncMock
import pytest
from fastapi.testclient import TestClient
from app import main

def ideas():
 return {'reply':'给你多列几种相近的餐食，实际价格在平台核对。','recommendations':[{'name':name,'keyword':name,'category':'米饭套餐','reason':'同类米饭套餐，可以核对口味后选择。','difference':'不同主料或做法'} for name in ['黄焖鸡米饭','香菇滑鸡饭','叉烧饭','卤肉饭','鸡腿饭','烧鸭饭','咖喱鸡饭','番茄炒蛋饭']]}

@pytest.fixture()
def client(tmp_path,monkeypatch):
 monkeypatch.setenv('FOODMOMENT_DB',str(tmp_path/'ideas.sqlite'))
 monkeypatch.delenv('DASHSCOPE_API_KEY',raising=False)
 with TestClient(main.app) as client:
  client.get('/api/state');yield client

def test_recommendations_use_real_model_adapter_but_never_claim_live_shops(client,monkeypatch):
 model=AsyncMock(return_value={'content':json.dumps(ideas())});monkeypatch.setattr(main,'qwen_turn',model)
 before=client.get('/api/state').json()['data']
 response=client.post('/api/takeaway/recommend',json={'message':'想吃米饭，多给选择','budget':20,'preference':'少辣'})
 assert response.status_code==200
 data=response.json()['data'];assert len(data['cards'])==8 and data['provider']=='qwen'
 for card in data['cards']:
  url=urlsplit(card['searchUrl'])
  assert url.scheme=='https' and url.netloc=='h5.waimai.meituan.com'
  assert url.path=='/waimai/mindex/searchresults'
  assert parse_qs(url.query)=={'queryType':['12002'],'keyword':[card['keyword']],'entranceId':['0'],'qwTypeId':['0'],'mode':['search']}
  assert card['source']=='ai-meal-idea' and not any(key in card for key in ['price','rating','shopName','paymentUrl'])
 assert client.get('/api/state').json()['data']==before
 payload=json.loads(model.call_args.args[0][1]['content']);assert payload['budget']==20 and payload['preference']=='少辣'

@pytest.mark.parametrize('case',['duplicate','excluded','url_keyword','fake_price','money_claim','unknown_category','too_few','invalid_json'])
def test_invalid_or_repeated_cards_are_not_returned(client,monkeypatch,case):
 output=ideas();excluded=[]
 if case=='duplicate':output['recommendations'][1]['keyword']=output['recommendations'][0]['keyword']
 if case=='excluded':excluded=['黄焖鸡米饭']
 if case=='url_keyword':output['recommendations'][0]['keyword']='https://evil.example'
 if case=='fake_price':output['recommendations'][0]['price']=12
 if case=='money_claim':output['recommendations'][0]['reason']='这份通常售价15-20元，保证符合预算。'
 if case=='unknown_category':output['recommendations'][0]['category']='invented'
 if case=='too_few':output['recommendations']=output['recommendations'][:1]
 monkeypatch.setattr(main,'qwen_turn',AsyncMock(return_value={'content':'invalid' if case=='invalid_json' else json.dumps(output)}))
 response=client.post('/api/takeaway/recommend',json={'message':'更多相似选择','budget':25,'excludeKeywords':excluded})
 assert response.status_code==502 and 'data' not in response.json()

def test_invalid_price_estimate_is_retried_before_returning_cards(client,monkeypatch):
 invalid=ideas();invalid['recommendations'][0]['reason']='只需要15元'
 model=AsyncMock(side_effect=[{'content':json.dumps(invalid)},{'content':json.dumps(ideas())}]);monkeypatch.setattr(main,'qwen_turn',model)
 response=client.post('/api/takeaway/recommend',json={'message':'想吃米饭','budget':20})
 assert response.status_code==200 and model.await_count==2
 assert all('15元' not in card['reason'] for card in response.json()['data']['cards'])

def test_missing_key_and_invalid_input_are_explicit(client):
 assert client.post('/api/takeaway/recommend',json={'message':'米饭','budget':25}).status_code==503
 for body in [{'message':' ','budget':25},{'message':'米饭','budget':0},{'message':'米饭','budget':201},{'message':'米饭','budget':25,'excludeKeywords':['a'*25]}]:
  assert client.post('/api/takeaway/recommend',json=body).status_code==422

def test_updated_frontend_entry_cannot_be_reused_from_http_cache(client):
 for path in ['/','/index.html','/?v=takeaway-results-20261103']:
  response=client.get(path)
  assert response.status_code==200
  assert response.headers['cache-control']=='no-store, max-age=0'
  assert response.headers['pragma']=='no-cache'
