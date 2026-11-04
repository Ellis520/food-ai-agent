"""Recognize an official example WAV through the real backend voice proxy."""
import asyncio
import json
import wave
from pathlib import Path
import httpx
import websockets

async def main():
    sample=next((Path(__file__).resolve().parent/'models').rglob('asr_example.wav'))
    with wave.open(str(sample)) as audio:
        if (audio.getframerate(),audio.getnchannels(),audio.getsampwidth())!=(16000,1,2):
            raise RuntimeError('Expected mono 16kHz PCM16 example')
        pcm=audio.readframes(audio.getnframes())
    async with httpx.AsyncClient(base_url='http://127.0.0.1:8017') as client:
        response=await client.get('/api/state');response.raise_for_status()
        cookie=client.cookies.get('fm_session')
        async with websockets.connect('ws://127.0.0.1:8017/api/asr/stream',origin='http://127.0.0.1:8017',additional_headers={'Cookie':'fm_session='+cookie},open_timeout=10) as socket:
            for index in range(0,len(pcm),3200):
                await socket.send(pcm[index:index+3200])
                await asyncio.sleep(0.01)
            await socket.send(json.dumps({'stop':True}))
            while True:
                data=json.loads(await asyncio.wait_for(socket.recv(),90))
                if data.get('error'):raise RuntimeError(data['error'])
                print(data,flush=True)
                if data.get('mode') in {'2pass-offline','offline'}:
                    if not data.get('text','').strip():raise RuntimeError('Empty recognition')
                    print('REAL ASR PROXY TEST PASSED',flush=True);return

if __name__=='__main__':asyncio.run(main())
