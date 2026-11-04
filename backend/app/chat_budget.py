"""Classify clear budget scopes before selecting chat tools."""
import re
import unicodedata
from typing import Literal

BudgetScope = Literal['day', 'meal', 'unclear']

DAY = re.compile(r'一天|全天|全日|整天|每天|每日|三餐|早中晚|早午晚')
PER_MEAL = re.compile(r'(?:一|每|单|这|那)(?:一)?[餐顿]|人均|每人')
SLOT_TERMS = {
    '早餐': re.compile(r'早餐|早饭|早上.*吃|早点'),
    '午餐': re.compile(r'午餐|午饭|中餐|中午.*吃'),
    '晚餐': re.compile(r'晚餐|晚饭|晚上.*吃'),
    '加餐': re.compile(r'加餐|夜宵|宵夜|下午茶'),
}
BUDGET = re.compile(
    r'[零一二两三四五六七八九十百\d.]+\s*(?:元|块(?:钱)?)|'
    r'\d+(?:\.\d+)?\s*(?:以内|以下|左右|之内|封顶)'
)
MEAL_REQUEST = re.compile(r'怎么吃|吃什么|吃啥|吃点|能吃|够吃|想吃|推荐|搭配|安排|以内|以下|封顶')


def classify_budget(text: str) -> tuple[BudgetScope | None, list[str]]:
    text = unicodedata.normalize('NFKC', text)
    slots = [slot for slot, pattern in SLOT_TERMS.items() if pattern.search(text)]
    if PER_MEAL.search(text) and DAY.search(text):
        return ('unclear' if len(BUDGET.findall(text)) > 1 else 'meal'), slots
    if DAY.search(text) or {'早餐', '午餐', '晚餐'}.issubset(slots):
        return 'day', slots
    if PER_MEAL.search(text) or len(slots) == 1:
        return 'meal', slots
    # Mentions of historical spending are not requests to set a meal budget.
    if '预算' in text or (BUDGET.search(text) and (
        BUDGET.fullmatch(text.strip(' 。？！?!')) or
        MEAL_REQUEST.search(text) and not re.search(r'花了|付了|花掉|消费了', text)
    )):
        return 'unclear', slots
    return None, slots
