import {test,expect} from '../completeProfile'

test('网页报错时可复制同一菜名去App搜，剪贴板失败仍能手动选取',async({page})=>{
 await page.addInitScript(()=>{
  let first=true
  Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async(text:string)=>{
   if(first){first=false;throw new Error('Clipboard denied')}
   ;(window as unknown as {copiedForTest:string}).copiedForTest=text
  }}})
 })
 const cards=['清汤牛肉面','虾滑椰子鸡','叉烧饭','番茄鸡蛋面'].map(name=>({name,keyword:name,category:'面食粉类',reason:'界面验证示例',difference:'不同餐食',source:'demo-meal-idea',searchUrl:'https://invalid.example/not-used'}))
 await page.route('**/api/takeaway/recommend',route=>route.fulfill({json:{data:{reply:'界面验证示例',cards,provider:'demo',budgetTarget:25,note:'界面验证示例'},source:'test'}}))
 await page.goto('/?v=takeaway-help-test#/takeaway')
 await page.getByRole('textbox',{name:'外卖需求'}).fill('清汤牛肉面')
 await page.getByRole('button',{name:'给我一些推荐',exact:true}).click()
 const card=page.locator('.meal-idea-card').first()
 await card.getByRole('button',{name:'去美团外卖搜索',exact:true}).click()
 await card.getByRole('button',{name:'复制菜名，去 App 搜',exact:true}).click()
 await expect(card.getByRole('status')).toContainText('当前浏览器无法复制')
 await expect(card.getByRole('textbox',{name:'美团搜索菜名',exact:true})).toHaveValue('清汤牛肉面')
 await expect(card.getByRole('textbox',{name:'美团搜索菜名',exact:true})).toBeVisible()
 await card.getByText('尝试网页版（可能遇到 403）',{exact:true}).click()
 const href=(await card.getByRole('link',{name:/打开网页版搜索/}).getAttribute('href'))!
 expect(new URL(href).searchParams.get('keyword')).toBe('清汤牛肉面')
 const login=new URL((await card.getByRole('link',{name:/网页需要登录时/}).getAttribute('href'))!)
 expect(login.pathname).toBe('/login');expect(login.searchParams.get('back_url')).toBe(href)
 await expect(card.getByRole('textbox',{name:'备用美团搜索链接',exact:true})).toHaveValue(href)
 await expect(card.getByRole('link',{name:/美团外卖 App 官方下载/})).toHaveAttribute('href','https://waimai.meituan.com/mobile/download')
 await card.getByRole('button',{name:'复制网页结果链接',exact:true}).click()
 expect(await page.evaluate(()=>(window as unknown as {copiedForTest:string}).copiedForTest)).toBe(href)
 await card.getByRole('button',{name:'复制菜名，去 App 搜',exact:true}).click()
 expect(await page.evaluate(()=>(window as unknown as {copiedForTest:string}).copiedForTest)).toBe('清汤牛肉面')
 await expect(card.getByRole('status')).toContainText('已复制「清汤牛肉面」')
 await page.setViewportSize({width:390,height:844})
 await expect.poll(()=>page.locator('.sidebar').evaluate(el=>el.getBoundingClientRect().right)).toBeLessThanOrEqual(0)
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await card.scrollIntoViewIfNeeded();await page.evaluate(()=>(document.activeElement as HTMLElement)?.blur())
 await page.screenshot({path:'../takeaway-help-mobile-preview.png'})
})
