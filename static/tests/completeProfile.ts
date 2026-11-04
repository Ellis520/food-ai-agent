import {test as baseTest, type Page} from '@playwright/test'
export {expect, type Page} from '@playwright/test'

export async function completeProfile(page:Page){
 await page.getByLabel('昵称',{exact:true}).fill('测试同学')
 await page.getByRole('button',{name:'保存并进入'}).click()
 await page.getByRole('heading',{name:'欢迎来到食刻 AI'}).waitFor({state:'hidden'})
}

export const test=baseTest.extend({
 page:async({page},use)=>{
  await page.goto('/#/')
  await completeProfile(page)
  await use(page)
 },
})
