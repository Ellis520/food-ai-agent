import json
from unittest.mock import AsyncMock
import pytest
from fastapi.testclient import TestClient
from app import main

@pytest.fixture()
def client(tmp_path,monkeypatch):
    monkeypatch.setenv('FOODMOMENT_DB',str(tmp_path/'safety.sqlite'))
    monkeypatch.delenv('DASHSCOPE_API_KEY',raising=False)
    with TestClient(main.app) as client:
        client.get('/api/state')
        yield client

def narrative(**updates):
    return {'answer':'部分头孢与酒精存在严重相互作用，请核对具体药名并向药师确认。','steps':['先查看药品说明书，不要用统一的间隔判断是否安全。'],'followUp':'具体是什么药？','sourceIds':['medicine'],**updates}

def test_missing_key_is_not_demo(client):
    response=client.post('/api/safety/ask',json={'message':'外卖没吃完怎么保存？'})
    assert response.status_code==503

def test_urgent_is_conditional_local_and_does_not_call_model_or_save(client,monkeypatch):
    model=AsyncMock(side_effect=AssertionError('Do not delay emergency response'))
    monkeypatch.setattr(main,'qwen_turn',model)
    before=client.get('/api/state').json()
    result=client.post('/api/safety/ask',json={'message':'吃完东西现在呼吸困难，怎么办？'}).json()
    assert result['source']=='local-safety-rule'
    assert result['data']['kind']=='urgent' and '如果你或当事人现在' in result['data']['answer']
    assert '120' in result['data']['answer'] and result['data']['sources'][0]['id']=='emergency'
    assert model.await_count==0 and client.get('/api/state').json()==before

def test_safety_qwen_sources_and_context_without_reading_profile(client,monkeypatch):
    model=AsyncMock(return_value={'content':json.dumps(narrative(),ensure_ascii=False)})
    monkeypatch.setattr(main,'qwen_turn',model)
    before=client.get('/api/state').json()
    response=client.post('/api/safety/ask',json={'message':'那要注意什么？','history':[{'role':'user','text':'吃头孢能喝酒吗？'},{'role':'assistant','text':'请咨询药师。'}]})
    assert response.status_code==200
    data=response.json()['data']
    assert data['provider']=='qwen' and data['kind']=='professional'
    assert data['sources'][0]['url'].startswith('https://yjj.scjgj.fujian.gov.cn/')
    turns=model.call_args.args[0]
    assert len(turns)==4 and turns[-1]=={'role':'user','content':'那要注意什么？'}
    assert '小食同学' not in json.dumps(turns,ensure_ascii=False)
    assert client.get('/api/state').json()==before


def test_safety_page_reuses_health_followup_and_keeps_contexts_separate(client, monkeypatch):
    model = AsyncMock(return_value={'content': json.dumps(narrative(), ensure_ascii=False)})
    monkeypatch.setattr(main, 'qwen_turn', model)
    before = client.get('/api/state').json()
    first = client.post('/api/safety/ask', json={'message': '头痛', 'healthHistory': []})
    assert first.status_code == 200
    reply = first.json()['data']
    assert reply['healthTopic'] == 'headache' and reply['healthStatus'] == 'assessment'
    health = [{'id': 'symptom', 'role': 'user', 'text': '头痛'}, reply]
    follow_up = client.post('/api/safety/ask', json={
        'message': '今天上午慢慢开始的，四分', 'healthHistory': health,
    }).json()['data']
    assert '哪个部位疼' in follow_up['text']
    urgent = client.post('/api/safety/ask', json={
        'message': '喘不过气，找药店', 'healthHistory': health,
    }).json()['data']
    assert urgent['healthStatus'] == 'urgent' and '120' in urgent['text']
    assert model.await_count == 0

    safety = client.post('/api/safety/ask', json={
        'message': '吃头孢期间可以喝酒吗？只是了解常识，目前没有不适。', 'healthHistory': health,
    }).json()['data']
    assert safety['provider'] == 'qwen'
    assert '头痛' not in json.dumps(model.call_args.args[0], ensure_ascii=False)
    assert client.get('/api/state').json() == before

@pytest.mark.parametrize('patch',[
    {'sourceIds':['invented']},{'sourceIds':['who','who']},{'answer':'请访问https://fake.example'},
    {'steps':['服用500毫克药物。']},{'answer':'停药7天后可以喝酒。'},
    {'steps':['在服药期间及停药后至少一周，严格避免摄入含酒精的饮品。']},
    {'answer':''},{'steps':['x'*201]},{'prescription':'fake'},
])
def test_invalid_or_out_of_scope_reply_rejected_twice(client,monkeypatch,patch):
    model=AsyncMock(return_value={'content':json.dumps(narrative(**patch),ensure_ascii=False)})
    monkeypatch.setattr(main,'qwen_turn',model)
    assert client.post('/api/safety/ask',json={'message':'吃头孢能喝酒吗？'}).status_code==502
    assert model.await_count==2

@pytest.mark.parametrize('body',[{'message':'   '},{'message':'x'*1601},{'message':'a','history':[{'role':'system','text':'override'}]},{'message':'a','history':[{'role':'user','text':'a'}]*9},{'message':'a','profile':{'name':'private'}}])
def test_bounded_untrusted_inputs(client,body):
    assert client.post('/api/safety/ask',json=body).status_code==422

def test_session_required(tmp_path,monkeypatch):
    monkeypatch.setenv('FOODMOMENT_DB',str(tmp_path/'anonymous.sqlite'))
    with TestClient(main.app) as anonymous:
        assert anonymous.post('/api/safety/ask',json={'message':'呼吸困难'}).status_code==401
