# Publishing this prepared repository

This ZIP contains the existing Git history from `santunesigor/leafcare` plus the prepared commits. Do not use GitHub's **Add file > Upload files** if you want to preserve these commits.

## Recommended method

1. Extract the ZIP.
2. Open PowerShell inside the extracted `leafcare` folder.
3. Check the included history:

```powershell
git log --oneline --decorate --graph --all
git status
git remote -v
```

4. Push the prepared commits:

```powershell
git push origin main
```

The remote is already configured as `https://github.com/santunesigor/leafcare.git`.

GitHub may ask you to authenticate in the browser or through Git Credential Manager. No credentials are stored in this ZIP.

## If the remote advanced after this ZIP was created

Do not force-push. First run:

```powershell
git pull --rebase origin main
git push origin main
```

Resolve any conflict locally, run the checks again and continue the rebase. Use `git status` to see the next required action.

## Important

- The datasets are not included.
- APKs and Keras checkpoints are intentionally ignored.
- Publish APKs later through GitHub Releases.
- Review `ROADMAP.md` before describing the project as production-ready.
