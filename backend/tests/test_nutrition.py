from copy import deepcopy
import json
import pytest
from fastapi.testclient import TestClient
from app import main

def recipe():
    return {'id':'r1','name':'营养表抄录测试','items':[{'id':'e1','name':'包装示例','grams':200,'source':'label','per100':{'kcal':100,'protein':10,'carbs':12,'fat':3,'fiber':None,'sodium':0}}]}

@pytest.fixture()
def client(tmp_path,monkeypatch):
    monkeypatch.setenv('FOODMOMENT_DB',str(tmp_path/'nutrition.sqlite'))
    with TestClient(main.app) as client:
        client.get('/api/state');yield client

def test_recipe_refresh_nulls_legacy_clients_isolation_and_no_meal_insertion(client):
    state=client.get('/api/state').json()['data'];state['state']['nutritionRecipes']=[recipe()]
    assert client.put('/api/state',json=state).status_code==200
    saved=client.get('/api/state').json()['data']
    assert saved['state']['nutritionRecipes'][0]['items'][0]['per100']['fiber'] is None
    assert saved['state']['nutritionRecipes'][0]['items'][0]['per100']['sodium']==0
    assert saved['state']['meals']==[]
    with TestClient(main.app) as other:
        assert other.get('/api/state').json()['data']['state']['nutritionRecipes']==[]
    # A still-open older page editing another field must preserve new recipes.
    saved['state'].pop('nutritionRecipes');saved['state']['profile']['name']='旧页面改昵称'
    assert client.put('/api/state',json=saved).status_code==200
    updated=client.get('/api/state').json()['data'];assert len(updated['state']['nutritionRecipes'])==1
    assert client.put('/api/state',json=saved).status_code==409
    updated['state']['nutritionRecipes']=[]
    assert client.put('/api/state',json=updated).status_code==200
    assert client.get('/api/state').json()['data']['state']['nutritionRecipes']==[]

@pytest.mark.parametrize('case',['negative','missing_all','bad_source','source_conflict','unknown_food','duplicate_item','duplicate_recipe','too_many_items','too_many_recipes','blank_name'])
def test_invalid_recipe_is_rejected_without_state_mutation(client,case):
    original=client.get('/api/state').json()['data'];body=deepcopy(original);value=recipe();entry=value['items'][0]
    if case=='negative':entry['per100']['sodium']=-1
    if case=='missing_all':entry['per100']={}
    if case=='bad_source':entry['source']='authoritative-database'
    if case=='source_conflict':entry['foodId']='chicken'
    if case=='unknown_food':entry.update(source='sample',foodId='unknown')
    if case=='duplicate_item':value['items'].append(deepcopy(entry))
    if case=='too_many_items':value['items']=[{**entry,'id':str(i)} for i in range(21)]
    if case=='blank_name':value['name']='   '
    body['state']['nutritionRecipes']=[value]
    if case=='duplicate_recipe':body['state']['nutritionRecipes'].append(deepcopy(value))
    if case=='too_many_recipes':body['state']['nutritionRecipes']=[{**value,'id':str(i)} for i in range(31)]
    assert client.put('/api/state',json=body).status_code==422
    assert client.get('/api/state').json()['data']==original

def test_qwen_estimates_only_requested_values_and_keeps_records(client,monkeypatch):
    original=client.get('/api/state').json()['data']
    foods=[{'name':'麻辣烫','missing':['kcal','protein']},{'name':'未知套餐','missing':['sodium']}]
    async def fake_qwen(turns,**options):
        assert options['structured']
        assert json.loads(turns[-1]['content'])==[{**food,'index':i} for i,food in enumerate(foods)]
        return {'content':json.dumps({'estimates':[
            {'index':1,'per100':{'sodium':-1}},
            {'index':0,'per100':{'kcal':120,'protein':None,'fat':30}},
            {'index':2,'per100':{'kcal':200}},
        ]})}
    monkeypatch.setattr(main,'qwen_turn',fake_qwen)
    result=client.post('/api/nutrition/estimate',json={'foods':foods})
    assert result.status_code==200
    assert result.json()=={'data':[{'kcal':120},{}],'source':'qwen-nutrition-estimate'}
    assert client.get('/api/state').json()['data']==original
