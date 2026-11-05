import {test,expect} from '../completeProfile'
test('保存桌面与手机预览',async({page})=>{
 await page.setViewportSize({width:1440,height:1100});await page.goto('/#/');await expect(page.locator('.hero')).toBeVisible();await page.screenshot({path:'../desktop-preview.png',fullPage:true});await page.setViewportSize({width:390,height:844});await expect.poll(()=>page.locator('.sidebar').evaluate(el=>el.getBoundingClientRect().right)).toBeLessThanOrEqual(0);await page.screenshot({path:'../mobile-preview.png',fullPage:true});await page.getByRole('button',{name:'打开导航'}).click();await expect(page.locator('.sidebar')).toHaveClass(/open/);await page.locator('nav').getByRole('link',{name:'饮食记录'}).click();await expect(page.locator('h1')).toContainText('记录一餐');await expect(page.locator('.sidebar')).not.toHaveClass(/open/)
})
test('全部路由桌面/手机可访问，无水平溢出、脚本错误',async({page})=>{
 const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message))
 for(const width of [1440,390]){await page.setViewportSize({width,height:900});for(const path of ['/','/chat','/plan','/records','/habits','/map','/takeaway','/nutrition','/language','/profile']){
 await page.goto('/#'+path);await expect(page.locator('h1')).toBeVisible();await expect(page.locator('h1')).not.toHaveText('');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 }}expect(errors).toEqual([])
})
test('记录增加、首页同步、刷新持久化、删除',async({page})=>{
 await page.goto('/#/records');await page.getByRole('button',{name:'记下这一餐'}).click();await expect(page.getByText('3 条记录')).toBeVisible();await page.reload();await expect(page.getByText('3 条记录')).toBeVisible();await page.goto('/#/');await expect(page.locator('.hero')).toBeVisible();await page.goto('/#/records');await page.getByRole('button',{name:'删除记录'}).last().click();await expect(page.getByText('2 条记录')).toBeVisible()
})
test('聊天卡片、语音占位、地图安全导航',async({page})=>{
 await page.goto('/#/chat');await page.getByRole('textbox',{name:'聊天消息'}).fill('看看附近餐厅');await page.getByRole('button',{name:'发送消息'}).click();await expect(page.locator('.structured-card')).toContainText('校园好味探索');await page.getByRole('button',{name:'按住语音输入'}).click();await expect(page.getByText('演示模式暂不支持录音。')).toBeVisible();await page.goto('/#/map');await page.getByRole('button',{name:'去导航',exact:true}).first().click();await expect(page.getByRole('dialog')).toContainText('无法提供真实路线');expect(await page.getByRole('dialog').locator('a').count()).toBe(0)
})
test('计划保存与饮食安全规则演示',async({page})=>{
 await page.goto('/#/plan');await page.getByRole('button',{name:'生成示例计划'}).click();await expect(page.locator('.plan-card')).toHaveCount(3);await page.getByRole('button',{name:'保存这份计划'}).click();await page.reload();await expect(page.locator('.plan-card')).toHaveCount(3);await page.goto('/#/language');await page.getByRole('textbox',{name:'饮食安全问题'}).fill('吃头孢期间可以喝酒吗？');await page.getByRole('button',{name:'发送问题',exact:true}).click();await expect(page.getByRole('article',{name:'安全问答回复'})).toContainText('预置规则演示');await page.getByRole('button',{name:'按住语音提问',exact:true}).click();await expect(page.getByText('演示模式暂不支持录音。')).toBeVisible()
})
test('默认演示明确标记规则回复',async({page})=>{
 await page.route('**/api/chat',route=>route.fulfill({status:503,body:'unavailable'}));
 // 此断言覆盖默认 demo 不发出真实网络调用；API 模式失败另见手工联调说明。
 await page.goto('/#/chat');await page.getByRole('textbox',{name:'聊天消息'}).fill('午餐');await page.getByRole('button',{name:'发送消息'}).click();await expect(page.locator('.structured-card')).toContainText('规则演示')
})

test('饮食回顾演示标记来源，不请求真实AI，切换范围清除旧回顾',async({page})=>{
 let calls=0
 await page.route('**/api/diet/analysis',route=>{calls++;return route.abort()})
 await page.goto('/#/habits')
 await page.getByRole('button',{name:'生成演示回顾',exact:true}).click()
 await expect(page.getByRole('article',{name:'饮食回顾报告'})).toContainText('规则演示 · 未调用 AI')
 await expect(page.getByRole('button',{name:'下载回顾'})).toBeVisible()
 expect(calls).toBe(0)
 await page.getByLabel('回顾范围').selectOption('30')
 await expect(page.getByRole('article',{name:'饮食回顾报告'})).toHaveCount(0)
 await expect(page.locator('.metric').first()).toContainText('/ 30 天')
})

test('外卖演示显示本地示例商家及来源',async({page})=>{
 let calls=0
 await page.route('**/api/food/search?**',route=>{calls++;return route.abort()})
 await page.goto('/#/takeaway')
 await page.getByRole('textbox',{name:'外卖需求'}).fill('想吃轻食')
 await page.getByRole('button',{name:'给我一些推荐',exact:true}).click()
 const card=page.getByRole('region',{name:'外卖商家推荐'}).getByRole('article')
 await expect(card).toContainText('青禾轻食')
 await expect(card).toContainText('演示商家')
 expect(calls).toBe(0)
})
