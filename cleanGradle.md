cd "D:\Academics (Masters)\FPSCompress\fpscompress-template-1.21.11"

# Clean project cache (PowerShell style)
rm -Recurse -Force .gradle -ErrorAction SilentlyContinue
rm -Recurse -Force build -ErrorAction SilentlyContinue

# Clean user Gradle cache
rm -Recurse -Force "$env:USERPROFILE\.gradle\daemon" -ErrorAction SilentlyContinue
rm -Recurse -Force "$env:USERPROFILE\.gradle\caches\journal-1" -ErrorAction SilentlyContinue

# Test Gradle works
.\gradlew --version