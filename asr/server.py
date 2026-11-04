"""Local CPU Paraformer streaming + offline correction, 16kHz PCM16 mono."""
import asyncio
import json
import os
import re
from pathlib import Path

ROOT=Path(__file__).resolve().parent
os.environ.setdefault('MODELSCOPE_CACHE',str(ROOT/'models'))
os.environ.setdefault('HF_HOME',str(ROOT/'models'/'huggingface'))
os.environ.setdefault('TORCH_HOME',str(ROOT/'models'/'torch'))
os.environ.setdefault('MODELSCOPE_DOWNLOAD_PARALLEL_WORKERS','4')
os.environ.setdefault('MODELSCOPE_DOWNLOAD_PARALLEL_THRESHOLD_MB','100')

def clean_transcript(text,strip=True):
    cleaned=re.sub(r'(?<=[\u3400-\u9fff])\s+(?=[\u3400-\u9fff])','',text or '')
    return cleaned.strip() if strip else cleaned

async def main():
    import numpy as np
    import websockets
    from funasr import AutoModel
    print('Loading Paraformer streaming and offline models. First start downloads model files.',flush=True)
    online=AutoModel(model='paraformer-zh-streaming',device='cpu',ncpu=4,disable_update=True,trust_remote_code=False)
    offline=AutoModel(model='paraformer-zh',device='cpu',ncpu=4,disable_update=True,trust_remote_code=False)
    lock=asyncio.Lock()
    async def infer(model,data,**options):
        async with lock:
            return await asyncio.to_thread(model.generate,input=data,**options)
    async def session(ws):
        audio=bytearray();pending=bytearray();cache={}
        try:
            async for message in ws:
                if isinstance(message,bytes):
                    if len(message)%2: raise ValueError('PCM16 must be aligned')
                    audio.extend(message);pending.extend(message)
                    if len(audio)>3_840_000: raise ValueError('Recording longer than 120 seconds')
                    while len(pending)>=19200:
                        chunk=np.frombuffer(bytes(pending[:19200]),dtype='<i2').astype(np.float32)/32768.0
                        del pending[:19200]
                        result=await infer(online,chunk,cache=cache,is_final=False,chunk_size=[5,10,5],encoder_chunk_look_back=4,decoder_chunk_look_back=1)
                        await ws.send(json.dumps({'mode':'2pass-online','text':clean_transcript(result[0].get('text',''),strip=False),'is_final':False},ensure_ascii=False))
                else:
                    control=json.loads(message)
                    if control.get('is_speaking') is False:
                        if not audio:
                            await ws.send(json.dumps({'mode':'2pass-offline','text':'','is_final':True}));return
                        tail=np.frombuffer(bytes(pending),dtype='<i2').astype(np.float32)/32768.0
                        if len(tail): await infer(online,tail,cache=cache,is_final=True,chunk_size=[5,10,5],encoder_chunk_look_back=4,decoder_chunk_look_back=1)
                        samples=np.frombuffer(bytes(audio),dtype='<i2').astype(np.float32)/32768.0
                        result=await infer(offline,samples)
                        await ws.send(json.dumps({'mode':'2pass-offline','text':clean_transcript(result[0].get('text','')),'is_final':True},ensure_ascii=False))
                        return
        except Exception:
            await ws.close(code=1011,reason='ASR inference failed')
    print('FunASR ready: ws://127.0.0.1:10095',flush=True)
    async with websockets.serve(session,'127.0.0.1',10095,max_size=200000,ping_interval=20):
        await asyncio.Future()

if __name__=='__main__':asyncio.run(main())
