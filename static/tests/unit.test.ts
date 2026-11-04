import {test} from 'node:test'
import assert from 'node:assert/strict'
import {nutrition,summarizeNutrition,sampleEntry,energyToKcal,nutritionBreakdown,recordNutrition} from '../src/api/nutrition.ts'
import type {NutritionEntry} from '../src/api/types.ts'
import {foods,buildPlan} from '../src/api/mock.ts'
import {mealSearchUrl,mealLoginUrl} from '../src/api/takeawayIdeas.ts'

test('美团直达结果列表，中文菜名保留且不能注入其他参数',()=>{
 const url=new URL(mealSearchUrl('老鸭粉丝汤 & 牛肉面'))
 assert.equal(url.origin,'https://h5.waimai.meituan.com')
 assert.equal(url.pathname,'/waimai/mindex/searchresults')
 assert.deepEqual(Object.fromEntries(url.searchParams),{queryType:'12002',keyword:'老鸭粉丝汤 & 牛肉面',entranceId:'0',qwTypeId:'0',mode:'search'})
 const login=new URL(mealLoginUrl('老鸭粉丝汤 & 牛肉面'))
 assert.equal(login.origin,'https://h5.waimai.meituan.com');assert.equal(login.pathname,'/login')
 assert.equal(login.searchParams.get('force'),'true');assert.equal(login.searchParams.get('back_url'),url.href)
})
test('营养按克重线性换算，钠保留 mg 单位',()=>{
 const result=nutrition(foods[1],250)
 assert.equal(result.kcal,390);assert.equal(result.protein,30);assert.equal(result.sodium,525)
})
test('拒绝非法份量，避免静默 NaN',()=>{for(const weight of [0,-1,NaN,Infinity,2001])assert.throws(()=>nutrition(foods[0],weight))})
test('分类估算按实际克数换算，粉面的纤维区分餐次，缺少基准的餐食不计入摄入',()=>{
 const meal={id:'meal',date:'2026-11-04',foodId:'',cost:10,grams:450,slot:'早餐',dishName:'肠粉'}
 const riceRoll=recordNutrition(meal)
 assert.equal(riceRoll.source,'estimate')
 assert.deepEqual(summarizeNutrition([riceRoll],true).values,{kcal:600,protein:20,carbs:80,fat:20,fiber:2,sodium:1600})
 const noodles={...meal,dishName:'粉面',grams:400}
 assert.equal(summarizeNutrition([recordNutrition(noodles)],true).values.fiber,3)
 assert.equal(summarizeNutrition([recordNutrition({...noodles,slot:'午餐'})],true).values.fiber,2)
 for(const dishName of ['快餐','麻辣烫']){
  const entry=recordNutrition({...meal,slot:'午餐',dishName})
  assert.equal(summarizeNutrition([entry],true).values.kcal,null)
 }
})
test('模板三餐引用有效食物且计算低预算实际花费',()=>{
 const plan=buildPlan(25,'不限');assert.equal(plan.length,3)
 assert.ok(plan.every(p=>foods.some(f=>f.id===p.foodId)&&p.grams>0))
 // 模板最低价为 32；低预算应由 UI 清楚提示超额。
 assert.equal(plan.reduce((s,p)=>s+foods.find(f=>f.id===p.foodId)!.price,0),32)
})

test('搭配汇总先合计再舍入，未知不冒充0且来源分开',()=>{
 const label:NutritionEntry={id:'label',name:'包装抄录示例',grams:200,source:'label',per100:{kcal:100,protein:10,carbs:12,fat:3,fiber:null,sodium:0}}
 const summary=summarizeNutrition([sampleEntry(foods[1],150,'sample'),label])
 assert.equal(summary.values.kcal,434);assert.equal(summary.values.protein,38)
 assert.equal(summary.values.sodium,315);assert.equal(summary.values.fiber,3)
 assert.equal(summary.missing.fiber,1);assert.equal(summary.totalGrams,350)
 assert.equal(summary.labelCount,1);assert.equal(summary.sampleCount,1)
 const onlyUnknown=summarizeNutrition([label]);assert.equal(onlyUnknown.values.fiber,null);assert.equal(onlyUnknown.values.sodium,0)
 const tiny={...label,grams:0.03,per100:{...label.per100,kcal:100}}
 assert.equal(summarizeNutrition([tiny,{...tiny,id:'other'}]).values.kcal,0.1)
})

test('包装kJ换算与非法搭配数据校验',()=>{
 assert.equal(energyToKcal(418.4,'kJ'),100);assert.equal(energyToKcal(100,'kcal'),100)
 assert.throws(()=>energyToKcal(NaN,'kJ'))
 const entry=sampleEntry(foods[1],100,'test')
 for(const grams of [0,-1,NaN,Infinity,2001])assert.throws(()=>summarizeNutrition([{...entry,grams}]))
 assert.throws(()=>summarizeNutrition([{...entry,per100:{...entry.per100,sodium:-1}}]))
 assert.throws(()=>summarizeNutrition(Array.from({length:21},()=>entry)))
 assert.equal(summarizeNutrition([]).values.kcal,null)
})

test('分项贡献使用已知总量，未知不分配比例，0合计不发生除0',()=>{
 const a=sampleEntry(foods[1],100,'a'),b={...a,id:'b',per100:{...a.per100,protein:null}}
 let rows=nutritionBreakdown([a,b],'protein')
 assert.equal(rows[0].value,12);assert.equal(rows[0].share,100);assert.equal(rows[1].value,null);assert.equal(rows[1].share,null)
 rows=nutritionBreakdown([a,b],'kcal');assert.deepEqual(rows.map(row=>row.share),[50,50])
 rows=nutritionBreakdown([{...a,per100:{...a.per100,sodium:0}}],'sodium')
 assert.equal(rows[0].value,0);assert.equal(rows[0].share,null)
})
