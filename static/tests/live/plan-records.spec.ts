import {test,expect,type Page} from '../completeProfile'

async function seed(page:Page){
 await page.goto('/#/')
 await expect(page.locator('h1')).toBeVisible()
 const [today,tomorrow]=await page.evaluate(()=>[0,1].map(offset=>{const d=new Date();d.setDate(d.getDate()+offset);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}))
 const data=(await (await page.request.get('/api/state')).json()).data
 data.state.plans={
  [today]:[{slot:'早餐',foodId:'oats',grams:250},{slot:'午餐',foodId:'chicken',grams:350},{slot:'晚餐',foodId:'salad',grams:300}],
  [tomorrow]:[{slot:'早餐',foodId:'egg',grams:100},{slot:'午餐',foodId:'noodle',grams:300},{slot:'晚餐',foodId:'salad',grams:300}]
 }
 data.state.meals=[]
 expect((await page.request.put('/api/state',{data})).status()).toBe(200)
 await page.reload()
 return {today,tomorrow}
}
async function savedMeals(page:Page){return (await (await page.request.get('/api/state')).json()).data.state.meals}

test('计划确认、去重、编辑和删除联动真实SQLite、首页与习惯分析',async({page})=>{
 const {today}=await seed(page)
 const progress=page.getByRole('region',{name:'计划打卡'})
 await expect(progress).toContainText('已记录 0 / 3 餐')
 await progress.getByRole('link',{name:'确认早餐用餐'}).click()
 await expect(page.getByLabel('食用份量（g）')).toHaveValue('250')
 await page.getByLabel('食用份量（g）').fill('220')
 await page.getByLabel('花费（元）').fill('7.5')
 await page.getByRole('button',{name:'记下这一餐',exact:true}).click()
 await expect(page.locator('.record-row')).toHaveCount(1)
 await expect(page.getByRole('button',{name:'记下这一餐',exact:true})).toBeDisabled()
 await expect.poll(async()=>(await savedMeals(page))[0]?.planRef).toBe(`${today}:早餐`)
 await page.reload()
 await expect(page.getByRole('button',{name:'记下这一餐',exact:true})).toBeDisabled()
 await page.setViewportSize({width:390,height:844})
 await expect.poll(()=>page.locator('.sidebar').evaluate(el=>el.getBoundingClientRect().right)).toBeLessThanOrEqual(0)
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.evaluate(()=>{(document.activeElement as HTMLElement)?.blur();window.scrollTo(0,0)})
 await page.screenshot({path:'../records-mobile-preview.png',fullPage:true})
 await page.getByRole('button',{name:'编辑记录'}).click()
 await page.getByLabel('食用份量（g）').fill('180')
 await page.getByLabel('花费（元）').fill('6')
 await page.getByRole('button',{name:'保存修改',exact:true}).click()
 await expect.poll(async()=>(await savedMeals(page))[0]?.grams).toBe(180)
 expect((await savedMeals(page)).length).toBe(1)
 await page.goto('/#/')
 await expect(page.locator('.stat').nth(1)).toContainText('1 餐')
 await expect(page.locator('.stat').nth(2)).toContainText('¥6')
 await expect(page.getByRole('region',{name:'计划打卡'})).toContainText('已记录 1 / 3 餐')
 await page.goto('/#/habits')
 await expect(page.locator('.metric').nth(2)).toContainText('¥6.0')
 await expect(page.locator('.insight')).toContainText('你记录了 1 餐')
 await page.goto('/#/records')
 await page.getByRole('button',{name:'删除记录'}).click()
 await expect.poll(async()=>(await savedMeals(page)).length).toBe(0)
 await page.goto('/#/')
 await expect(page.getByRole('link',{name:'确认早餐用餐'})).toBeVisible()
 await page.evaluate(()=>{(document.activeElement as HTMLElement)?.blur();window.scrollTo(0,0)})
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.screenshot({path:'../checkin-mobile-preview.png',fullPage:true})
})

test('未来计划和失效计划不可误记，调整餐次后解除原计划关联',async({page})=>{
 const {today,tomorrow}=await seed(page)
 await page.goto('/#/plan')
 await page.locator('.week-tabs button').nth(1).click()
 const progress=page.getByRole('region',{name:'计划打卡'})
 await expect(progress.getByText('到用餐当天再记录')).toHaveCount(3)
 await expect(progress.getByRole('link',{name:/确认.*用餐/})).toHaveCount(0)
 await page.goto(`/#/records?planDate=${tomorrow}&slot=${encodeURIComponent('早餐')}`)
 await expect(page.getByRole('alert')).toContainText('尚未到用餐日期')
 await expect(page.getByRole('button',{name:'记下这一餐',exact:true})).toBeDisabled()
 await page.goto(`/#/records?planDate=${today}&slot=${encodeURIComponent('加餐')}`)
 await expect(page.getByRole('alert')).toContainText('计划不存在')
 await page.getByRole('button',{name:'改为手动记录'}).click()
 await expect(page.getByRole('button',{name:'记下这一餐',exact:true})).toBeEnabled()
 await page.goto(`/#/records?planDate=${today}&slot=${encodeURIComponent('早餐')}`)
 await page.getByLabel('餐次',{exact:true}).selectOption('加餐')
 await expect(page.getByText('本次保存将作为独立记录')).toBeVisible()
 await page.getByRole('button',{name:'记下这一餐',exact:true}).click()
 await expect.poll(async()=>(await savedMeals(page)).length).toBe(1)
 expect((await savedMeals(page))[0].planRef).toBeUndefined()
 await page.goto('/#/')
 await expect(page.getByRole('region',{name:'计划打卡'})).toContainText('已记录 0 / 3 餐')
})
