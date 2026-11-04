"""Tongyi Qwen transport and rate-limit handling."""
import asyncio
import os

import httpx
from fastapi import HTTPException

def qwen_limit_reason(result: httpx.Response):
    try:
        body = result.json()
        error = body.get('error', body)
        code = str(error.get('code', '')).lower()
        message = str(error.get('message', '')).lower()
    except (AttributeError, ValueError, TypeError):
        code = message = ''
    if 'budgetlimitexceeded' in code:
        return '千问的百炼用量预算已达上限，请在百炼控制台检查预算设置。', False
    if 'freetieronly' in code or 'free allocated quota exceeded' in message:
        return '千问的免费额度已用完或到期，请在百炼控制台检查模型额度。', False
    if any(word in code for word in ('billoverdue', 'commoditynotpurchased')):
        return '千问服务当前不可用，请在百炼控制台检查账单和服务状态。', False
    if (code.startswith('throttling') or code in {'limit_requests','limit_burst_rate','rate_limit_exceeded','resourceexhausted'}
            or 'too many requests' in message or 'rate limit' in message):
        return '千问暂时达到调用频率限制，请稍等片刻再试。', True
    return '千问暂时无法受理请求（429）。请稍后重试，若持续出现请检查百炼用量和限流状态。', False

async def qwen_turn(messages, *, tools=None, structured=False,max_tokens=1800):
    key = os.getenv('DASHSCOPE_API_KEY')
    if not key:
        raise HTTPException(503,'千问未配置：请在 backend/.env 填写 DASHSCOPE_API_KEY。')
    payload = {'model':os.getenv('QWEN_MODEL','qwen-plus'),'messages':messages,'temperature':0.4,'max_tokens':max_tokens}
    if tools:
        payload['tools'] = tools
        payload['tool_choice'] = 'auto'
    if structured:
        payload['response_format']={'type':'json_object'}
        payload['enable_thinking']=False
    try:
        async with httpx.AsyncClient(timeout=45) as client:
            url = os.getenv('QWEN_BASE_URL','https://dashscope.aliyuncs.com/compatible-mode/v1').rstrip('/')+'/chat/completions'
            for attempt in range(2):
                result = await client.post(url, headers={'Authorization':f'Bearer {key}'}, json=payload)
                if result.status_code not in (403, 429):
                    break
                detail, temporary = qwen_limit_reason(result)
                if result.status_code == 403:
                    if '免费额度' in detail:
                        raise HTTPException(403, detail)
                    break
                retry_after = result.headers.get('Retry-After')
                delay = 1 if retry_after is None else int(retry_after) if retry_after.isdecimal() else None
                if temporary and not attempt and delay is not None and delay <= 3:
                    await asyncio.sleep(max(1, delay))
                    continue
                raise HTTPException(429, detail)
            if result.status_code != 200:
                raise HTTPException(502,f'千问服务返回 {result.status_code}，请检查密钥、模型权限及额度。')
            message = result.json()['choices'][0]['message']
            if not message.get('tool_calls') and not str(message.get('content') or '').strip():
                raise ValueError('empty content')
            return message
    except (httpx.HTTPError,KeyError,ValueError,TypeError):
        raise HTTPException(502,'千问服务响应异常或超时，请稍后重试。')
