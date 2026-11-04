import {test,expect,type Page} from '../completeProfile'
import {readFile} from 'node:fs/promises'

async function seed(page:Page){
 await page.goto('/#/habits');await expect(page.locator('h1')).toBeVisible()
 const dates=await page.evaluate(()=>[0,6,10,40,-1].map(offset=>{const d=new Date();d.setDate(d.getDate()-offset);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}))
 const data=(await (await page.request.get('/api/state')).json()).data
 data.state.meals=dates.map((date,index)=>({id:`analysis-seed-${index}`,date,slot:index===0?'早餐':'午餐',foodId:index===0?'oats':'noodle',grams:250,cost:[8,12,15,1000,1000][index]}))
 expect((await page.request.put('/api/state',{data})).status()).toBe(200)
 await page.reload();await expect(page.locator('.metric').last()).toContainText('¥20.00')
}

test('7/30天筛选，空记录和服务失败明确显示且不冒充AI',async({page})=>{
 await page.goto('/#/habits')
 await expect(page.getByRole('button',{name:'生成 AI 回顾',exact:true})).toBeDisabled()
 await seed(page)
 await page.getByLabel('回顾范围').selectOption('30')
 await expect(page.locator('.metric').first()).toContainText('3 / 30 天')
 await expect(page.locator('.metric').last()).toContainText('¥35.00')
 await page.route('**/api/diet/analysis',route=>route.fulfill({status:503,json:{detail:'千问未配置：测试失败提示'}}))
 await page.getByRole('button',{name:'生成 AI 回顾',exact:true}).click()
 await expect(page.getByRole('alert')).toContainText('503')
 await expect(page.getByRole('article',{name:'饮食回顾报告'})).toHaveCount(0)
 await expect(page.getByRole('button',{name:'生成 AI 回顾',exact:true})).toBeEnabled()
})

test('生成回顾等待正在进行的记录保存，再使用保存后的版本',async({page})=>{
 await seed(page)
 let release!:()=>void,seen!:()=>void,calls=0,analysisRevision=-1
 const gate=new Promise<void>(resolve=>{release=resolve}),putSeen=new Promise<void>(resolve=>{seen=resolve})
 await page.route('**/api/state',async route=>{
  if(route.request().method()==='PUT'){seen();await gate}
  await route.continue()
 })
 await page.route('**/api/diet/analysis',async route=>{calls++;analysisRevision=route.request().postDataJSON().revision;await route.fulfill({status:503,json:{detail:'等待保存后的测试响应'}})})
 try{
  await page.goto('/#/records');await page.getByRole('button',{name:'记下这一餐',exact:true}).click();await putSeen
  await page.goto('/#/habits')
  await page.getByRole('button',{name:'生成 AI 回顾',exact:true}).click()
  await expect(page.getByRole('button',{name:'正在生成回顾…',exact:true})).toBeDisabled()
  expect(calls).toBe(0)
  release()
  await expect(page.getByRole('alert')).toContainText('等待保存后的测试响应')
  expect(calls).toBe(1)
  expect(analysisRevision).toBe((await (await page.request.get('/api/state')).json()).data.revision)
 }finally{release()}
})

test('真实千问回顾展示统计依据、可下载，手机无溢出且不改记录',async({page})=>{
 test.skip(process.env.RUN_LIVE_SERVICES!=='1','Real Qwen analysis is opt-in')
 await seed(page)
 await page.getByLabel('回顾范围').selectOption('30')
 const before=(await (await page.request.get('/api/state')).json()).data
 const result=page.waitForResponse(response=>response.url().endsWith('/api/diet/analysis')&&response.request().method()==='POST')
 await page.getByRole('button',{name:'生成 AI 回顾',exact:true}).click()
 const response=await result;expect(response.status()).toBe(200)
 const report=(await response.json()).data
 expect(report.provider).toBe('qwen');expect(report.statistics.recordCount).toBe(3);expect(report.statistics.totalCost).toBe(35)
 const card=page.getByRole('article',{name:'饮食回顾报告'})
 await expect(card).toContainText('千问真实生成')
 await expect(card.locator('.observation')).toHaveCount(report.observations.length)
 await expect(card.locator('.evidence').first()).toContainText('统计依据')
 const downloaded=page.waitForEvent('download');await page.getByRole('button',{name:'下载回顾'}).click()
 const download=await downloaded
 expect(download.suggestedFilename()).toMatch(/^foodmoment-review-.*\.txt$/)
 const content=await readFile((await download.path())!,'utf-8')
 expect(content).toContain('千问真实生成');expect(content).toContain('已记录花费 ¥35.00')
 expect((await (await page.request.get('/api/state')).json()).data).toEqual(before)
 await page.screenshot({path:'../habits-ai-preview.png',fullPage:true})
 await page.setViewportSize({width:390,height:844})
 await expect.poll(()=>page.locator('.sidebar').evaluate(el=>el.getBoundingClientRect().right)).toBeLessThanOrEqual(0)
 await page.evaluate(()=>{(document.activeElement as HTMLElement)?.blur();window.scrollTo(0,0)})
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.screenshot({path:'../habits-ai-mobile-preview.png',fullPage:true})
 await page.reload();await expect(page.getByRole('article',{name:'饮食回顾报告'})).toHaveCount(0)
})
