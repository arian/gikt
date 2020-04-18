backup:
	cp -rf .git "../gikt-backups/gikt-git-$(shell date '+%Y-%m-%d-%H%M')"

.PHONY: backup
