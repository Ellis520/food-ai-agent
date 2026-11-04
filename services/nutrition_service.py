"""Request and validate Qwen nutrition estimates without storing records."""
import json

from fastapi import HTTPException
from pydantic import ValidationError

from core.prompts import NUTRITION_SYSTEM_PROMPT
from backend.app.nutrition_models import EstimateRequest, Nutrients


async def estimate_missing_nutrients(body:EstimateRequest,qwen_turn):
    foods=[{'index':index,'name':food.name,'missing':sorted(food.missing)} for index,food in enumerate(body.foods)]
    result=await qwen_turn([
        {'role':'system','content':NUTRITION_SYSTEM_PROMPT},
        {'role':'user','content':json.dumps(foods,ensure_ascii=False)},
    ],structured=True,max_tokens=3000)
    try:
        response=json.loads(result.get('content') or '')
        rows=response.get('estimates') if isinstance(response,dict) else None
        if not isinstance(rows,list):
            raise ValueError('invalid estimates')
    except (ValueError,TypeError):
        raise HTTPException(502,'千问营养估算格式无效，请稍后重试。')
    estimates=[{} for _ in foods]
    for row in rows:
        if not isinstance(row,dict):
            continue
        index,values=row.get('index'),row.get('per100')
        if type(index) is not int or not 0<=index<len(foods) or not isinstance(values,dict):
            continue
        try:
            verified=Nutrients.model_validate({key:values.get(key) for key in foods[index]['missing']},strict=True)
        except ValidationError:
            continue
        estimates[index]=verified.model_dump(exclude_none=True)
    return estimates
