import {completeProfile, expect, test} from '../completeProfile'

test.use({baseURL:'http://127.0.0.1:5174'})

test('美食圈从导航发图文动态，另一会话可搜索和留言约饭', async({page, browser})=>{
 const origin = 'http://127.0.0.1:5174'
 const second = await browser.newContext({viewport:{width:390,height:844}})
 try {
  const visitor = await second.newPage()
  const dish = '测试咖喱饭 '+Date.now()
  await page.goto('/#/')
  await page.getByRole('navigation',{name:'主导航'}).getByRole('link',{name:'美食圈'}).click()
  await page.getByRole('button',{name:'写动态',exact:true}).click()
  await page.getByLabel('动态类型').selectOption('recommend')
  await page.getByLabel('菜名',{exact:true}).fill(dish)
  await page.getByLabel('店名或地点').fill('测试食堂')
  await page.getByLabel('动态内容').fill('推荐这份饭，酱汁配米饭很香。')
  const png = await page.evaluate(()=>{
   const canvas = document.createElement('canvas')
   canvas.width = 24
   canvas.height = 16
   const context = canvas.getContext('2d')!
   context.fillStyle = '#ca7042'
   context.fillRect(0,0,24,16)
   return canvas.toDataURL('image/png').split(',')[1]
  })
  await page.locator('input[type=file]').setInputFiles({name:'test.png',mimeType:'image/png',buffer:Buffer.from(png,'base64')})
  await expect(page.getByAltText('待上传图片 1')).toBeVisible()
  await page.getByRole('button',{name:'发布动态',exact:true}).click()
  await expect(page.getByRole('heading',{name:dish,exact:true})).toBeVisible()

  await visitor.goto(origin+'/#/community')
  await completeProfile(visitor)
  await visitor.getByRole('button',{name:'美食推荐',exact:true}).click()
  await visitor.getByLabel('搜索菜名、店名或帖子内容').fill(dish)
  await visitor.getByRole('button',{name:'搜索',exact:true}).click()
  const result = visitor.locator('article').filter({hasText:dish})
  await expect(result).toHaveCount(1)
  await expect.poll(async()=>result.getByAltText('动态图片 1').evaluate((img:HTMLImageElement)=>img.naturalWidth)).toBeGreaterThan(0)
  await expect(result.getByRole('button',{name:'删除我的动态'})).toHaveCount(0)
  await visitor.getByRole('button',{name:'避雷分享',exact:true}).click()
  await expect(visitor.getByText('没有找到符合条件的动态')).toBeVisible()

  await page.getByRole('button',{name:'写动态',exact:true}).click()
  await page.getByLabel('动态类型').selectOption('buddy')
  const meetup = '测试见面点 '+Date.now()
  await page.getByLabel('见面地点').fill(meetup)
  await page.getByLabel('约饭时间').fill('周五 12:15')
  await page.getByLabel('动态内容').fill('中午一起去吃饭吗？')
  await page.getByRole('button',{name:'发布动态',exact:true}).click()
  await expect(page.getByText(meetup,{exact:true})).toBeVisible()

  await visitor.getByLabel('搜索菜名、店名或帖子内容').fill('')
  await visitor.getByRole('button',{name:'找饭搭子',exact:true}).click()
  const invitation = visitor.locator('article').filter({hasText:meetup})
  await invitation.getByRole('button',{name:/留言约饭/}).click()
  await invitation.getByRole('button',{name:'我想一起',exact:true}).click()
  await expect(invitation.getByLabel('公开留言')).toHaveValue('我想一起！还有位置吗？')
  await invitation.getByRole('button',{name:'发送留言',exact:true}).click()
  await expect(invitation.getByText('我想一起！还有位置吗？')).toBeVisible()
  expect(await visitor.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
  await page.reload()
  const ownInvitation = page.locator('article').filter({hasText:meetup})
  await ownInvitation.getByRole('button',{name:'留言约饭 · 1'}).click()
  await expect(ownInvitation.getByText('我想一起！还有位置吗？')).toBeVisible()

  page.on('dialog', dialog=>void dialog.accept())
  await ownInvitation.getByRole('button',{name:'删除我的动态'}).click()
  await page.locator('article').filter({hasText:dish}).getByRole('button',{name:'删除我的动态'}).click()
  await expect(page.locator('article').filter({hasText:meetup})).toHaveCount(0)
 } finally {
  await second.close()
 }
})
