"""Aggregate saved records before asking the provider for a bounded narrative."""
from collections import Counter
from datetime import date, timedelta
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field

class AnalysisRequest(BaseModel):
    model_config=ConfigDict(extra='forbid')
    days: Literal[7,30]=7
    revision: int=Field(ge=0)

class Observation(BaseModel):
    model_config=ConfigDict(extra='forbid',str_strip_whitespace=True)
    title: str=Field(min_length=1,max_length=40)
    detail: str=Field(min_length=1,max_length=240)
    evidence: Literal['coverage','breakfast','spending','distribution']

class Suggestion(BaseModel):
    model_config=ConfigDict(extra='forbid',str_strip_whitespace=True)
    title: str=Field(min_length=1,max_length=40)
    action: str=Field(min_length=1,max_length=200)

class AnalysisNarrative(BaseModel):
    model_config=ConfigDict(extra='forbid',str_strip_whitespace=True)
    summary: str=Field(min_length=1,max_length=240)
    observations: list[Observation]=Field(min_length=1,max_length=3)
    suggestions: list[Suggestion]=Field(min_length=1,max_length=3)

def summarize_records(state:dict,days:int,today:date,food_names:dict):
    dates=[(today-timedelta(days=i)).isoformat() for i in range(days-1,-1,-1)]
    meals=[meal for meal in state['meals'] if meal['date'] in dates]
    custom_foods = Counter(meal['dishName'] for meal in meals if not meal['foodId'] and meal.get('dishName'))
    recorded_days=len({meal['date'] for meal in meals})
    total=round(sum(meal['cost'] for meal in meals),2)
    return {
        'period':{'days':days,'start':dates[0],'end':dates[-1]},
        'statistics':{
            'recordCount':len(meals),'recordedDays':recorded_days,
            'breakfastDays':len({meal['date'] for meal in meals if meal['slot']=='早餐'}),
            'totalCost':total,'averageRecordedDayCost':round(total/recorded_days,2) if recorded_days else 0,
            'mealCounts':{slot:sum(meal['slot']==slot for meal in meals) for slot in ['早餐','午餐','晚餐','加餐']},
            'daily':[{'date':day,'recordCount':sum(meal['date']==day for meal in meals),'totalCost':round(sum(meal['cost'] for meal in meals if meal['date']==day),2)} for day in dates],
            'foods':[{'foodId':food_id,'name':name,'count':sum(meal['foodId']==food_id for meal in meals)} for food_id,name in food_names.items() if any(meal['foodId']==food_id for meal in meals)]
                    + [{'foodId': 'custom:' + name, 'name': name, 'count': count} for name, count in custom_foods.items()],
        },
    }
