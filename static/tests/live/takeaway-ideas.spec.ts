import {test,expect} from '../completeProfile'

test('高德推荐可切换排序，分店直达和复制入口正确',async({page,context})=>{
 const share='https://dpurl.cn/6ruMd4Sz'
 const match='肯德基(上渡路店)'
 const other='肯德基(另一分店)'
 const noMetrics='肯德基(未知数据分店)'
 const photo='https://photos.example.test/restaurant.svg'
 const unavailable='https://photos.example.test/unavailable.svg'
 const base={address:'测试地址',source:'amap'}
 const restaurants=[
  {...base,id:'1',name:match,photoUrl:photo,price:30,rating:4.7,distance:80},
  {...base,id:'2',name:other,photoUrl:unavailable,price:20,rating:4.3,distance:100},
  {...base,id:'3',name:noMetrics,price:null,rating:null,distance:null},
 ]
 await page.route(photo,route=>route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="30" height="20"><rect width="30" height="20" fill="green"/></svg>'}))
 await page.route(unavailable,route=>route.abort())
 await context.grantPermissions(['clipboard-read','clipboard-write'])
 await page.route('**/api/food/search?**',route=>{
  expect(new URL(route.request().url()).searchParams.get('keyword')).toBe('肯德基')
  return route.fulfill({json:{source:'amap',data:{restaurants,hasMore:false}}})
 })
 await page.setViewportSize({width:390,height:844})
 await page.goto('/#/takeaway')
 await page.getByRole('textbox',{name:'外卖需求'}).fill('想吃KFC炸鸡')
 await page.getByRole('button',{name:'给我一些推荐',exact:true}).click()
 const cards=page.getByRole('region',{name:'外卖商家推荐'}).getByRole('article')
 await expect(cards.locator('h3')).toHaveText([match,other,noMetrics])
 await expect(cards.first().getByRole('link',{name:/美团门店/})).toHaveAttribute('href',share)
 const img=cards.first().getByRole('img',{name:match+'的商家照片'})
 await img.scrollIntoViewIfNeeded()
 await expect.poll(()=>img.evaluate(el=>(el as HTMLImageElement).naturalWidth)).toBeGreaterThan(0)
 await expect(cards.first()).toContainText('商家图片 · 高德')
 const alternate=cards.nth(1)
 await alternate.scrollIntoViewIfNeeded()
 await expect(alternate).toContainText('照片暂时无法加载')
 await expect(cards.nth(2)).toContainText('暂无店铺照片')
 const searchUrl=new URL((await alternate.getByRole('link',{name:/美团搜店名/}).getAttribute('href'))!)
 expect(searchUrl.searchParams.get('keyword')).toBe(other)
 await expect(alternate.getByRole('link',{name:/打开淘宝闪购/})).toHaveAttribute('href',/^https:\/\/links\.ele\.me/)
 await alternate.getByRole('button',{name:'复制店名'}).click()
 await expect(alternate.getByRole('status')).toContainText('已复制店名')
 expect(await page.evaluate(()=>navigator.clipboard.readText())).toBe(other)
 const sort=page.getByRole('combobox',{name:'推荐因子'})
 await sort.selectOption('price')
 await expect(cards.locator('h3')).toHaveText([other,match,noMetrics])
 await sort.selectOption('distance')
 await expect(cards.locator('h3')).toHaveText([match,other,noMetrics])
 await page.getByRole('spinbutton',{name:'人均上限（元）'}).fill('25')
 await expect(cards.locator('h3')).toHaveText([other])
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})

test('高德失败时保留重试入口',async({page})=>{
 await page.route('**/api/food/search?**',route=>route.fulfill({status:503,json:{detail:'测试：高德暂不可用'}}))
 await page.goto('/#/takeaway')
 await page.getByRole('textbox',{name:'外卖需求'}).fill('20元左右想吃米饭')
 await page.getByRole('button',{name:'给我一些推荐',exact:true}).click()
 await expect(page.getByRole('alert')).toContainText('503')
 await expect(page.getByRole('button',{name:'重试查询',exact:true})).toBeEnabled()
 await expect(page.getByRole('spinbutton',{name:'人均上限（元）'})).toHaveValue('20')
})
