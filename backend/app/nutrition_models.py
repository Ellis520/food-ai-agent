"""Saved nutrition calculations and transient nutrition estimate requests."""
from pydantic import BaseModel,ConfigDict,Field,model_validator
from typing import Literal

class Model(BaseModel):
    model_config=ConfigDict(extra='forbid',allow_inf_nan=False,str_strip_whitespace=True)

class Nutrients(Model):
    kcal:float|None=Field(default=None,ge=0,le=1000)
    protein:float|None=Field(default=None,ge=0,le=100)
    carbs:float|None=Field(default=None,ge=0,le=100)
    fat:float|None=Field(default=None,ge=0,le=100)
    fiber:float|None=Field(default=None,ge=0,le=100)
    sodium:float|None=Field(default=None,ge=0,le=100000)
    @model_validator(mode='after')
    def some_values(self):
        if all(value is None for value in self.model_dump().values()):raise ValueError('至少填写一项营养值')
        return self

class NutritionEntry(Model):
    id:str=Field(min_length=1,max_length=100)
    name:str=Field(min_length=1,max_length=60)
    grams:float=Field(gt=0,le=2000)
    per100:Nutrients
    source:Literal['sample','label']
    foodId:Literal['oats','chicken','noodle','salad','rice','egg']|None=None
    @model_validator(mode='after')
    def source_matches(self):
        if self.source=='sample' and self.foodId is None:raise ValueError('示例食物需要foodId')
        if self.source=='label' and self.foodId is not None:raise ValueError('自填数值不能冒充示例目录')
        return self

class NutritionRecipe(Model):
    id:str=Field(min_length=1,max_length=100)
    name:str=Field(min_length=1,max_length=60)
    items:list[NutritionEntry]=Field(min_length=1,max_length=20)
    @model_validator(mode='after')
    def unique_entries(self):
        if len({item.id for item in self.items})!=len(self.items):raise ValueError('搭配中的食物ID不能重复')
        return self

NutrientKey = Literal['kcal','protein','carbs','fat','fiber','sodium']

class EstimateFood(Model):
    name:str=Field(min_length=1,max_length=60)
    missing:set[NutrientKey]=Field(min_length=1,max_length=6)

class EstimateRequest(Model):
    foods:list[EstimateFood]=Field(min_length=1,max_length=20)
