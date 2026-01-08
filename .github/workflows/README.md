# CI/CD Workflow Documentation

## Overview

GitHub Actions workflow untuk automated **build**, **validation**, dan **patching** Battery Monitor app.

### Pipeline Architecture

```
Trigger (Push/PR/Tag)
  |
  v
[validate-policies] ──────┐
  |✓                      │
  ├─> [patch-sepolicy]     │
  │     |✓                 │
  │     └─> Artifacts      │
  │                         │
  └─> [build-apk]          │
        |✓                 │
        └─> Artifacts       │
                            │
                            v
                    [create-release]
                      (on tags only)
                            |✓
                            └─> GitHub Release
```

---

## Triggers

### 1. Automatic Triggers

#### Push to Master/Main
```bash
# Any push ke master/main akan trigger workflow
git push origin master
```

**Runs:**
- ✅ validate-policies
- ✅ patch-sepolicy (jika prebuilt/ files ada)
- ✅ build-apk (jika Gradle project ada)
- ❌ create-release (skip, only on tags)

#### Pull Requests
```bash
# PR ke master/main akan trigger validation
gh pr create --base master --head feature-branch
```

**Runs:**
- ✅ validate-policies (prevent merging broken policies)
- ❌ patch-sepolicy (skip untuk PR)
- ❌ build-apk (optional, bisa enable)
- ❌ create-release (skip)

#### Version Tags
```bash
# Create dan push tag untuk trigger release
git tag v1.0.0
git push origin v1.0.0
```

**Runs:**
- ✅ validate-policies
- ✅ patch-sepolicy
- ✅ build-apk
- ✅ create-release ⭐ **Auto-create GitHub release**

### 2. Manual Trigger (workflow_dispatch)

**Via GitHub UI:**
1. Go to [Actions tab](https://github.com/hoshiyomiX/webview/actions)
2. Select "Build and Patch" workflow
3. Click "Run workflow"
4. Choose branch
5. Set parameters:
   - `skip_build`: Skip APK compilation
   - `skip_patch`: Skip SELinux patching

**Via GitHub CLI:**
```bash
# Basic run
gh workflow run "Build and Patch" --ref selinux

# Skip APK build
gh workflow run "Build and Patch" --ref selinux \
  -f skip_build=true

# Skip patching
gh workflow run "Build and Patch" --ref selinux \
  -f skip_patch=true
```

---

## Jobs

### Job 1: validate-policies

**Purpose:** Validate SELinux policies sebelum merge/deploy

**Steps:**
1. Check CIL syntax (balanced parentheses)
2. Count policy rules
3. Validate file_contexts regex
4. Generate validation report

**Output:**
```
========================================
Validating CIL Syntax
========================================
✓ Found: selinux/vendor_sepolicy.cil
Open parentheses: 12456
Close parentheses: 12456
✓ CIL syntax valid (balanced parentheses)
Policy rules (allow): 34
Type definitions: 2
```

**Success Criteria:**
- ✅ Balanced parentheses
- ✅ At least 10 policy rules
- ✅ File contexts have valid regex

**Fails If:**
- ❌ Unbalanced parentheses
- ❌ Missing required files
- ❌ Invalid regex in file_contexts

---

### Job 2: patch-sepolicy

**Purpose:** Auto-merge original vendor files dengan Battery Monitor policies

**Conditional:** Only runs if `prebuilt/` files exist

**Steps:**
1. Check for `prebuilt/vendor_sepolicy.cil` and `prebuilt/vendor_file_contexts`
2. Run `patch_sepolicy.sh` script
3. Validate patched files
4. Generate checksums (MD5, SHA256)
5. Upload artifacts

**Artifacts Produced:**
```
selinux-patched/
├── vendor_sepolicy.cil.patched
├── vendor_file_contexts.patched
├── checksums.md5
├── checksums.sha256
└── patch_report.txt
```

**Retention:** 30 days

**Download:**
```bash
# Via GitHub CLI
gh run download <run-id> --name selinux-patched

# Or from Actions tab UI
```

---

### Job 3: build-apk

**Purpose:** Compile Android APK dengan multiple API levels

**Conditional:** Only runs if Gradle project exists

**Matrix Strategy:**
```yaml
matrix:
  api-level: [31, 33, 34]
```

**Parallel Builds:**
- API 31 (Android 12)
- API 33 (Android 13)
- API 34 (Android 14)

**Steps per API level:**
1. Setup Java 17 + Android SDK
2. Cache Gradle dependencies (~5min saved)
3. Build debug APK
4. Run unit tests
5. Run lint checks
6. Upload APK artifact

**Artifacts Produced:**
```
BatteryMonitor-debug-api31/
└── app-debug.apk

BatteryMonitor-debug-api33/
└── app-debug.apk

BatteryMonitor-debug-api34/
└── app-debug.apk

test-reports-api31/
├── tests/
└── test-results/
```

**Retention:** 30 days

---

### Job 4: create-release

**Purpose:** Auto-create GitHub release dengan artifacts

**Trigger:** Only on version tags (`v*.*.*`)

**Dependencies:**
- Requires all previous jobs to succeed
- validate-policies ✓
- patch-sepolicy ✓
- build-apk ✓

**Steps:**
1. Download all artifacts from previous jobs
2. Generate release notes
3. Create GitHub release
4. Upload assets:
   - APK files (all API levels)
   - Patched SELinux files
   - Checksums
   - Documentation

**Release Assets:**
```
Release v1.0.0
├── BatteryMonitor-debug-api31.apk
├── BatteryMonitor-debug-api33.apk
├── BatteryMonitor-debug-api34.apk
├── vendor_sepolicy.cil.patched
├── vendor_file_contexts.patched
├── checksums.md5
└── checksums.sha256
```

**Retention:** Permanent

---

## Caching

### Gradle Cache
```yaml
- uses: actions/setup-java@v4
  with:
    cache: 'gradle'  # Auto-cache ~/.gradle/
```

**Benefits:**
- ✅ ~5-10 minutes faster builds
- ✅ Reduced network bandwidth
- ✅ Consistent dependency versions

**Cache Key:**
- Based on `**/build.gradle*` files
- Invalidates on Gradle config changes

---

## Status Badges

### Add to README.md

**Markdown:**
```markdown
![Build Status](https://github.com/hoshiyomiX/webview/workflows/Build%20and%20Patch/badge.svg?branch=master)
```

**HTML (with link):**
```html
<a href="https://github.com/hoshiyomiX/webview/actions">
  <img src="https://github.com/hoshiyomiX/webview/workflows/Build%20and%20Patch/badge.svg?branch=master" alt="Build Status">
</a>
```

**Multiple Branches:**
```markdown
![Master](https://github.com/hoshiyomiX/webview/workflows/Build%20and%20Patch/badge.svg?branch=master)
![Selinux](https://github.com/hoshiyomiX/webview/workflows/Build%20and%20Patch/badge.svg?branch=selinux)
```

---

## Artifacts Management

### Download Artifacts

**Via GitHub UI:**
1. Go to [Actions tab](https://github.com/hoshiyomiX/webview/actions)
2. Click on workflow run
3. Scroll to "Artifacts" section
4. Click artifact name to download

**Via GitHub CLI:**
```bash
# List artifacts
gh run list --workflow="Build and Patch"

# Download specific artifact
gh run download <run-id> --name selinux-patched

# Download all artifacts
gh run download <run-id>
```

**Via API:**
```bash
# Get artifact list
curl -H "Authorization: token $GITHUB_TOKEN" \
  https://api.github.com/repos/hoshiyomiX/webview/actions/runs/<run-id>/artifacts

# Download artifact
curl -L -H "Authorization: token $GITHUB_TOKEN" \
  <artifact-download-url> -o artifact.zip
```

### Artifact Retention

| Artifact Type | Retention | Auto-Delete |
|---------------|-----------|-------------|
| APK files | 30 days | Yes |
| Patched files | 30 days | Yes |
| Test reports | 30 days | Yes |
| Release assets | Permanent | No |

---

## Troubleshooting

### Issue 1: Validation Failed - Unbalanced Parentheses

**Error:**
```
❌ CIL syntax error: Unbalanced parentheses!
Open parentheses: 12456
Close parentheses: 12455
```

**Solution:**
```bash
# Check syntax locally
$ bash -c 'OPEN=$(grep -o "(" selinux/vendor_sepolicy.cil | wc -l); CLOSE=$(grep -o ")" selinux/vendor_sepolicy.cil | wc -l); echo "Open: $OPEN, Close: $CLOSE"'

# Fix missing parenthesis
$ nano selinux/vendor_sepolicy.cil

# Re-push
$ git add selinux/vendor_sepolicy.cil
$ git commit -m "Fix CIL syntax"
$ git push
```

---

### Issue 2: Patching Skipped - No Prebuilt Files

**Warning:**
```
⚠️  No prebuilt files found, skipping patching
```

**Expected:** Ini normal jika kamu belum add original files

**To Enable Patching:**
```bash
# Extract original files dari ROM
$ adb pull /vendor/etc/selinux/vendor_sepolicy.cil prebuilt/
$ adb pull /vendor/etc/selinux/vendor_file_contexts prebuilt/

# Commit dan push
$ git add prebuilt/
$ git commit -m "Add original vendor SELinux files"
$ git push
```

---

### Issue 3: APK Build Failed - Gradle Error

**Error:**
```
Task :app:compileDebugJavaWithJavac FAILED
```

**Debug Locally:**
```bash
# Clean build
$ ./gradlew clean

# Build dengan stacktrace
$ ./gradlew assembleDebug --stacktrace

# Check Java version
$ java -version  # Should be 17
```

**Fix:**
1. Fix compilation errors
2. Commit changes
3. Push to trigger new build

---

### Issue 4: Cache Issues

**Symptom:** Builds using outdated dependencies

**Solution:**
```bash
# Manually clear cache via GitHub UI:
# Settings > Actions > Caches > Delete cache

# Or via API:
curl -X DELETE -H "Authorization: token $GITHUB_TOKEN" \
  https://api.github.com/repos/hoshiyomiX/webview/actions/caches
```

---

### Issue 5: Release Creation Failed

**Error:**
```
Error: Resource not accessible by integration
```

**Cause:** Missing `contents: write` permission

**Solution:** Already fixed in workflow:
```yaml
create-release:
  permissions:
    contents: write  # ✅ Required for creating releases
```

---

## Examples

### Success Output

**validate-policies:**
```
✓ Found: selinux/vendor_sepolicy.cil
✓ CIL syntax valid (balanced parentheses)
✓ File contexts valid

SELinux Policy Validation
| Metric               | Value   |
|----------------------|---------|
| CIL Syntax          | ✅ Valid |
| Allow Rules         | 34      |
| Type Definitions    | 2       |
| Total Contexts      | 45      |
| Battery Info Labels | 23      |
```

**patch-sepolicy:**
```
✓ Found original vendor_sepolicy.cil (1.2MiB)
✓ Merged vendor_sepolicy.cil
ℹ  Original lines: 45230
ℹ  Added lines: 156
ℹ  Total lines: 45386

Patching Results
✅ SELinux files patched successfully

Checksums
```
ab12cd34... vendor_sepolicy.cil.patched
ef56gh78... vendor_file_contexts.patched
```
```

**build-apk:**
```
BUILD SUCCESSFUL in 3m 24s
✅ Debug APK built successfully

Uploading artifact: BatteryMonitor-debug-api33
Artifact size: 4.2 MB
```

**create-release:**
```
🎉 Release Created
Version: v1.0.0

Release available at:
https://github.com/hoshiyomiX/webview/releases/tag/v1.0.0
```

---

## Best Practices

### 1. Test Before Pushing
```bash
# Validate policies locally
$ bash selinux/patch_sepolicy.sh

# Build APK locally
$ ./gradlew assembleDebug

# Only push if both succeed
$ git push origin master
```

### 2. Use Feature Branches
```bash
# Create feature branch
$ git checkout -b feature/new-sepolicy-rule

# Make changes
$ nano selinux/vendor_sepolicy.cil

# Push for PR (triggers validation)
$ git push origin feature/new-sepolicy-rule

# Create PR untuk review
$ gh pr create --base master
```

### 3. Semantic Versioning
```bash
# Major release (breaking changes)
$ git tag v2.0.0

# Minor release (new features)
$ git tag v1.1.0

# Patch release (bug fixes)
$ git tag v1.0.1

# Push tag to trigger release
$ git push origin v1.0.1
```

---

## Resources

- **Workflow File:** [`.github/workflows/build-and-patch.yml`](build-and-patch.yml)
- **Actions Runs:** [github.com/hoshiyomiX/webview/actions](https://github.com/hoshiyomiX/webview/actions)
- **GitHub Actions Docs:** [docs.github.com/actions](https://docs.github.com/en/actions)

---

**Last Updated:** 2026-01-08  
**Workflow Version:** 1.0  
**Author:** hoshiyomiX
