# Gitee 提交与协作

确认仓库地址后，将本地源码提交到团队仓库。新仓库选择空仓库即可。已有提交的仓库请先拉取或克隆，再在独立分支整理变更。

```powershell
git status --short
git remote -v
git push -u origin main
```

发起合并请求前运行 README 中的构建和测试命令。提交范围为源码、锁文件、配置示例、测试和文档。实际 `.env`、SQLite、试用账号、用户照片和依赖缓存留在本机。前端产物通过 `npm --prefix static run build` 生成。
