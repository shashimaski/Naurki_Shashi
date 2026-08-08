$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
Set-Location 'F:\views\g\Naukri'

# Commit 1: Task 5.1
git add `
  backend\src\main\java\com\adi\naukri\automation\NaukriSelectors.java `
  backend\src\main\java\com\adi\naukri\automation\AutomationStep.java `
  backend\src\main\java\com\adi\naukri\automation\StepResult.java `
  backend\src\test\java\com\adi\naukri\automation\NaukriSelectorsTest.java
git commit -m "feat(be): add NaukriSelectors, AutomationStep, StepResult"
Write-Host "Commit 1 SHA: $(git rev-parse HEAD)"

# Commit 2: Task 5.2 (includes pom.xml exec plugin addition)
git add `
  backend\pom.xml `
  backend\src\main\java\com\adi\naukri\automation\PlaywrightSession.java `
  backend\src\test\java\com\adi\naukri\automation\PlaywrightSessionIT.java
git commit -m "feat(be): add PlaywrightSession Chromium lifecycle wrapper"
Write-Host "Commit 2 SHA: $(git rev-parse HEAD)"

# Commit 3: Task 5.3
git add `
  backend\src\main\java\com\adi\naukri\automation\NaukriAutomator.java `
  backend\src\main\java\com\adi\naukri\automation\AutomatorConfig.java `
  backend\src\main\java\com\adi\naukri\automation\AutomatorException.java `
  backend\src\main\java\com\adi\naukri\automation\ManualLoginGate.java `
  backend\src\main\java\com\adi\naukri\automation\StepListener.java `
  backend\src\main\java\com\adi\naukri\automation\Automator.java `
  backend\src\test\java\com\adi\naukri\automation\NaukriAutomatorAgainstMockIT.java `
  backend\src\test\java\com\adi\naukri\automation\TestPorts.java `
  backend\src\test\java\com\adi\naukri\automation\ObjectMapperHolder.java
git commit -m "feat(be): NaukriAutomator with mock-server integration coverage"
Write-Host "Commit 3 SHA: $(git rev-parse HEAD)"

Write-Host "All commits done."
