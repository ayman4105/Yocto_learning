# BitBake Commands Reference

---

## 1. Setup Environment

```bash
source oe-init-build-env <builddir>
```

> **Important:** Always use `source`, never `bash`.

---

## 2. devshell & devpyshell

```bash
# Open bash shell inside recipe WORKDIR
bitbake <recipe> -c devshell

# Open python shell with BitBake datastore
bitbake <recipe> -c devpyshell
```

### Inside devpyshell
```python
d.getVar('SRC_URI')
d.getVar('LICENSE')
d.getVar('DEPENDS')
```

---

## 3. bitbake -e (Environment Dump)

```bash
# Dump global environment
bitbake -e

# Dump recipe environment
bitbake -e <recipe>

# Find specific variable value
bitbake -e | grep "^MACHINE="
bitbake -e nano | grep "^S="
bitbake -e nano | grep "^SRC_URI="
bitbake -e nano | grep "^WORKDIR="
bitbake -e nano | grep "^LICENSE="
bitbake -e nano | grep "^DEPENDS="

# Show history (who set this variable)
bitbake -e nano | grep -B 10 "^SRC_URI="
bitbake -e nano | grep -B 20 "^CFLAGS="

# Show lines after match
bitbake -e nano | grep -A 5 "^DEPENDS="

# Save full output to file
bitbake -e nano > nano_env.txt
