import {test,expect} from '../completeProfile'
// Opt-in: calls configured real map services, never logs credentials.
test.skip(process.env.RUN_LIVE_SERVICES!=='1','Set RUN_LIVE_SERVICES=1 to call real services')
test('真实高德地图画布与校园商家点位',async({page})=>{
 const diagnostics:string[]=[]
 page.on('console',m=>{if(m.type()==='error'||m.type()==='warning')diagnostics.push(m.text().replace(/[a-f0-9]{32}/gi,'[redacted]'))})
 page.on('requestfailed',r=>{const url=new URL(r.url());diagnostics.push(url.host+url.pathname+': '+r.failure()?.errorText)})
 await page.setViewportSize({width:1440,height:1000})
 await page.goto('/#/map')
 await expect(page.locator('h1')).toBeVisible()
 await expect.poll(()=>page.locator('.restaurant-card').count(),{timeout:30000}).toBeGreaterThan(0)
 await expect(page.locator('.amap-container')).toBeVisible({timeout:30000})
 await expect(page.locator('.real-map')).toHaveAttribute('data-map-ready','true',{timeout:40000})
 await expect.poll(()=>page.locator('.amap-marker').count(),{timeout:30000}).toBeGreaterThan(0)
 await expect.poll(()=>page.locator('.restaurant-cover img').evaluateAll(imgs=>imgs.filter(img=>(img as HTMLImageElement).complete&&(img as HTMLImageElement).naturalWidth>0).length),{timeout:30000}).toBeGreaterThan(0)
 await page.waitForLoadState('networkidle',{timeout:15000}).catch(()=>{})
 if(diagnostics.length)console.log('Map diagnostics:',JSON.stringify(diagnostics.slice(0,12)))
 await page.screenshot({path:'../map-preview.png'})
 await page.setViewportSize({width:390,height:844})
 await expect.poll(()=>page.locator('.sidebar').evaluate(el=>el.getBoundingClientRect().right)).toBeLessThanOrEqual(0)
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.waitForLoadState('networkidle',{timeout:10000}).catch(()=>{})
 await page.screenshot({path:'../map-mobile-preview.png'})
})
