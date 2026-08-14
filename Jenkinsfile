pipeline {
    agent any

    parameters {
        string(name: 'APP_VERSION', defaultValue: '0.1.0', description: 'Version being built/deployed')
        string(name: 'TARGET_VM_INVENTORY', defaultValue: 'inventory.ini', description: 'Ansible inventory file for the Windows target VM(s)')
        booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Run the Ansible deploy stage after a successful build')
    }

    stages {

        stage('1. Checkout') {
            steps {
                echo '===== CHECKOUT SOURCE CODE ====='
                git branch: 'main',
                    url: 'https://github.com/harshithapv15/Naukri.git'
            }
        }

        stage('2. Verify Environment') {
            steps {
                echo '===== VERIFY ENVIRONMENT ====='
                bat '''
                    echo ===== JAVA =====
                    java -version
                    echo ===== MAVEN =====
                    mvn -version
                    echo ===== NODE =====
                    node -v
                    echo ===== NPM =====
                    npm -v
                    echo ===== GIT =====
                    git --version
                '''
            }
        }

        stage('3. Fetch Java 17 JRE') {
            steps {
                echo '===== FETCH APPLICATION JRE ====='
                powershell '''
                    & "$env:WORKSPACE\\build\\fetch-jre.ps1"
                    if ($LASTEXITCODE -ne 0) {
                        exit $LASTEXITCODE
                    }
                '''
            }
        }

        stage('4. Install Playwright Chromium') {
            // NOTE: this bundles Chromium into electron\resources\ so it ships
            // INSIDE the .exe (see stage 8). It is a build-time step only —
            // do not remove it, and do not try to reproduce it on the target VM.
            steps {
                echo '===== INSTALL PLAYWRIGHT CHROMIUM (bundled into artifact) ====='
                powershell '''
                    & "$env:WORKSPACE\\build\\install-playwright.ps1"
                    if ($LASTEXITCODE -ne 0) {
                        exit $LASTEXITCODE
                    }
                '''
            }
        }

        stage('5. Build Backend') {
            steps {
                echo '===== BUILD BACKEND ====='
                bat '''
                    mvn -f backend\\pom.xml clean package -DskipTests -Dmaven.test.skip=true
                '''
            }
        }

        stage('6. Build Mock Server') {
            steps {
                echo '===== BUILD MOCK SERVER ====='
                bat '''
                    mvn -f mock-naukri\\pom.xml clean package -DskipTests -Dmaven.test.skip=true
                '''
            }
        }

        stage('7. Build Frontend') {
            steps {
                echo '===== BUILD FRONTEND ====='
                powershell '''
                    & "$env:WORKSPACE\\build\\phases\\build-frontend.ps1"
                    if ($LASTEXITCODE -ne 0) {
                        exit $LASTEXITCODE
                    }
                '''
            }
        }

        stage('8. Build Electron Application') {
            steps {
                echo '===== BUILD ELECTRON APPLICATION ====='
                powershell '''
                    & "$env:WORKSPACE\\build\\phases\\build-electron.ps1" -Variant Ship
                    if ($LASTEXITCODE -ne 0) {
                        exit $LASTEXITCODE
                    }
                '''
            }
        }

        stage('9. Verify Artifacts') {
            steps {
                echo '===== VERIFY ARTIFACTS ====='
                powershell '''
                    $dist = "$env:WORKSPACE\\dist"
                    if (-not (Test-Path $dist)) {
                        throw "dist directory does not exist"
                    }

                    Write-Host ""
                    Write-Host "===== BUILD ARTIFACTS ====="
                    Get-ChildItem $dist -Recurse -File |
                        Select-Object FullName, Length

                    $exeFiles = Get-ChildItem $dist -Recurse -Filter "*.exe"
                    if ($exeFiles.Count -eq 0) {
                        throw "No EXE artifacts found"
                    }

                    Write-Host ""
                    Write-Host "===== EXE ARTIFACTS FOUND ====="
                    foreach ($exe in $exeFiles) {
                        Write-Host $exe.FullName
                    }

                    Write-Host ""
                    Write-Host "Artifact verification SUCCESS"
                '''
            }
        }

        stage('10. Archive Artifacts') {
            steps {
                echo '===== ARCHIVING ARTIFACTS ====='
                archiveArtifacts artifacts: 'dist/**/*.exe',
                                  fingerprint: true
            }
        }

        stage('11. Deploy via Ansible') {
            when {
                expression { return params.DEPLOY }
            }
            steps {
                echo '===== DEPLOY TO WINDOWS VM VIA ANSIBLE ====='
                // Ansible controller must be reachable from this agent
                // (Linux agent, or WSL/Ansible-on-Windows if the agent is Windows).
                bat """
                    ansible-playbook -i ${params.TARGET_VM_INVENTORY} ansible\\install_naukri.yml ^
                        -e app_version=${params.APP_VERSION} ^
                        -e artifact_repo_path=%WORKSPACE%\\dist
                """
            }
        }
    }

    post {
        success {
            echo '''
            ========================================
            NAUKRI CI BUILD SUCCESS
            ========================================
            Artifacts successfully generated and archived.
            ========================================
            '''
        }
        failure {
            echo '''
            ========================================
            NAUKRI CI BUILD FAILED
            ========================================
            Check the first failed stage in Console Output.
            ========================================
            '''
        }
        always {
            echo '===== Jenkins CI pipeline finished ====='
        }
    }
}
