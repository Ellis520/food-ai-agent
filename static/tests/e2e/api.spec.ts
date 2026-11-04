import {test,expect} from '../completeProfile'
test.use({baseURL:'http://127.0.0.1:5174'})
test('API 模式服务错误不回退演示',async({page})=>{
 await page.route('**/api/chat',route=>route.fulfill({status:503,headers:{'Access-Control-Allow-Origin':'*'},body:'unavailable'}));await page.goto('/#/chat');await page.getByRole('textbox',{name:'聊天消息'}).fill('午餐');await page.getByRole('button',{name:'发送消息'}).click();await expect(page.getByRole('alert')).toContainText('503');await expect(page.locator('.structured-card')).toHaveCount(0)
})
test('API 模式成功渲染结构化响应',async({page})=>{
 await page.route('**/api/chat',route=>route.fulfill({json:{source:'test',data:{id:'server-message',role:'assistant',text:'接口契约测试成功',kind:'nutrition',source:'测试响应'}}}));await page.goto('/#/chat');await page.getByRole('textbox',{name:'聊天消息'}).fill('营养');await page.getByRole('button',{name:'发送消息'}).click();await expect(page.locator('.structured-card')).toContainText('测试响应');await expect(page.locator('.message.assistant')).toContainText('接口契约测试成功')
})
test('聊天找店可按分店打开美团搜索或高德步行路线',async({page})=>{
 const places=[
  {id:'one',name:'椰子鸡（东区店）',address:'东区门口',hours:'',distance:540,price:null,rating:4.3,lng:113.30,lat:23.09,source:'amap'},
  {id:'two',name:'椰子鸡（中大店）',address:'新港西路',hours:'',distance:920,price:63,rating:4.6,lng:113.31,lat:23.10,source:'amap'},
 ]
 let calls=0
 await page.route('**/api/chat', route=>{
  calls++
  return route.fulfill({json:{source:'qwen',data:{id:'lookup',role:'assistant',kind:'restaurant',text:'找到了两家椰子鸡店。',restaurantSearch:{keyword:'椰子鸡',places}}}})
 })
 await page.goto('/#/chat')
 const input=page.getByRole('textbox',{name:'聊天消息'})
 await input.fill('帮我找椰子鸡店')
 await page.getByRole('button',{name:'发送消息'}).click()
 const choices=page.getByRole('region',{name:'聊天找店结果'})
 await expect(choices).toContainText('您打算点外卖，还是到店吃？')
 await input.fill('我想去第二家吃')
 await page.getByRole('button',{name:'发送消息'}).click()
 const navigation=page.getByRole('region',{name:'到店导航卡片'})
 const navigationHref=await navigation.getByRole('link',{name:'去导航'}).getAttribute('href')
 const url=new URL(navigationHref!)
 expect(url.hostname).toBe('uri.amap.com')
 expect(url.searchParams.get('to')).toBe('113.31,23.1,椰子鸡（中大店）')
 expect(url.searchParams.get('mode')).toBe('walk')
 const first=choices.locator('article').filter({hasText:'椰子鸡（东区店）'})
 await first.getByRole('button',{name:'点外卖',exact:true}).click()
 const takeaway=page.getByRole('region',{name:'美团外卖卡片'})
 const meituan=new URL((await takeaway.getByRole('link',{name:'在美团外卖搜这家店'}).getAttribute('href'))!)
 expect(meituan.pathname).toBe('/waimai/mindex/searchresults')
 expect(meituan.searchParams.get('keyword')).toBe('椰子鸡（东区店）')
 await expect(takeaway.getByRole('button',{name:'复制店名，去 App 搜'})).toBeVisible()
 expect(calls).toBe(1)
})
