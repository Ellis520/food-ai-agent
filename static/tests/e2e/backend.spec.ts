import {test,expect} from '../completeProfile'
test.use({baseURL:'http://127.0.0.1:5174'})
test('真实 FastAPI SQLite：记录保存并刷新恢复',async({page})=>{
 await page.goto('/#/records');await expect(page.locator('h1')).toBeVisible();await expect(page.getByText('0 条记录')).toBeVisible();await page.getByRole('button',{name:'记下这一餐'}).click();await expect.poll(async()=>{const response=await page.request.get('/api/state');return response.json().then(r=>r.data.state.meals.length)}).toBe(1);await page.reload();await expect(page.getByText('1 条记录')).toBeVisible();await page.getByRole('button',{name:'删除记录'}).click();await expect.poll(async()=>{const response=await page.request.get('/api/state');return response.json().then(r=>r.data.state.meals.length)}).toBe(0)
})
test('真实后端缺少千问配置时明确报错',async({page})=>{
 await page.goto('/#/chat');await page.getByRole('textbox',{name:'聊天消息'}).fill('午餐');await page.getByRole('button',{name:'发送消息'}).click();await expect(page.getByRole('alert')).toContainText('千问未配置');await expect(page.locator('.structured-card')).toHaveCount(0)
})
