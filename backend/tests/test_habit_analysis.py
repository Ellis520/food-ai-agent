import json
from datetime import datetime,timedelta,timezone
from unittest.mock import AsyncMock
import pytest
from fastapi.testclient import TestClient
from app import main

NARRATIVE={'summary':'记录较少，仅回顾已记录内容。','observations':[{'title':'先核对记录','detail':'没有记录的日期无法判断是否用餐。','evidence':'coverage'}],'suggestions':[{'title':'补记实际用餐','action':'核对实际花费后补记一餐。'}]}

@pytest.fixture()
def prepared(tmp_path,monkeypatch):
 monkeypatch.setenv('FOODMOMENT_DB',str(tmp_path/'analysis.sqlite'))
 monkeypatch.delenv('DASHSCOPE_API_KEY',raising=False)
 today=datetime.now(timezone(timedelta(hours=8))).date()
 with TestClient(main.app) as client:
  state=client.get('/api/state').json()['data']
  state['state']['profile']['name']='不应发送给模型的名字'
  state['state']['meals']=[{'id':str(index),'date':(today-timedelta(days=days)).isoformat(),'slot':slot,'foodId':'oats','grams':200,'cost':cost} for index,(days,slot,cost) in enumerate([(0,'早餐',8),(0,'加餐',2),(6,'午餐',12),(7,'早餐',5),(29,'晚餐',20),(30,'晚餐',1000),(-1,'晚餐',1000)])]
  assert client.put('/api/state',json=state).status_code==200
  yield client,client.get('/api/state').json()['data']

@pytest.mark.parametrize('days,count,total,covered',[(7,3,22,2),(30,5,47,4)])
def test_analysis_uses_only_session_period_and_aggregates(prepared,monkeypatch,days,count,total,covered):
 client,state=prepared
 model=AsyncMock(return_value={'content':json.dumps(NARRATIVE)})
 monkeypatch.setattr(main,'qwen_turn',model)
 response=client.post('/api/diet/analysis',json={'days':days,'revision':state['revision']})
 assert response.status_code==200
 result=response.json()['data'];stats=result['statistics']
 assert stats['recordCount']==count and stats['recordedDays']==covered and stats['totalCost']==total
 assert stats['averageRecordedDayCost']==round(total/covered,2)
 assert len(stats['daily'])==days and result['provider']=='qwen'
 sent=json.loads(model.call_args.args[0][1]['content'])
 assert sent['statistics']==stats and 'records' not in sent and 'meals' not in sent
 assert '不应发送给模型的名字' not in json.dumps(sent,ensure_ascii=False)
 assert client.get('/api/state').json()['data']==state
 with TestClient(main.app) as other:
  other_state=other.get('/api/state').json()['data']
  assert other.post('/api/diet/analysis',json={'days':days,'revision':other_state['revision']}).status_code==422

def test_analysis_empty_and_invalid_range_do_not_call_model(prepared,monkeypatch):
 client,state=prepared;state['state']['meals']=[]
 assert client.put('/api/state',json=state).status_code==200
 revision=client.get('/api/state').json()['data']['revision']
 model=AsyncMock();monkeypatch.setattr(main,'qwen_turn',model)
 assert client.post('/api/diet/analysis',json={'days':7,'revision':revision}).status_code==422
 assert client.post('/api/diet/analysis',json={'days':90,'revision':revision}).status_code==422
 model.assert_not_called()

def test_analysis_unconfigured_and_stale_revision_are_explicit(prepared):
 client,state=prepared
 assert client.post('/api/diet/analysis',json={'days':7,'revision':state['revision']}).status_code==503
 assert client.post('/api/diet/analysis',json={'days':7,'revision':0}).status_code==409

@pytest.mark.parametrize('case',['invalid_json','unknown_evidence','empty_suggestions','extra_field'])
def test_analysis_rejects_invalid_provider_output(prepared,monkeypatch,case):
 client,state=prepared;output=json.loads(json.dumps(NARRATIVE))
 if case=='unknown_evidence':output['observations'][0]['evidence']='invented-health-score'
 if case=='empty_suggestions':output['suggestions']=[]
 if case=='extra_field':output['diagnosis']='invented'
 model=AsyncMock(return_value={'content':'invalid' if case=='invalid_json' else json.dumps(output)})
 monkeypatch.setattr(main,'qwen_turn',model)
 assert client.post('/api/diet/analysis',json={'days':7,'revision':state['revision']}).status_code==502
 assert client.get('/api/state').json()['data']==state

def test_analysis_discarded_if_data_changes_during_generation(prepared,monkeypatch):
 client,state=prepared
 async def updating_model(*args,**kwargs):
  with main.connect() as db:db.execute('UPDATE sessions SET revision=revision+1')
  return {'content':json.dumps(NARRATIVE)}
 monkeypatch.setattr(main,'qwen_turn',updating_model)
 response=client.post('/api/diet/analysis',json={'days':7,'revision':state['revision']})
 assert response.status_code==409 and '生成期间' in response.json()['detail']
