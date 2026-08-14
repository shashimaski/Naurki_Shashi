pipeline {
    agent any

    parameters {
        string(name: 'APP_VERSION', defaultValue: '0.1.0', description: 'Version being built/deployed')
        string(name: 'TARGET_VM_INVENTORY', defaultValue: 'inventory.ini', description: 'Ansible inventory file for the Windows target VM(s)')
        booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Run the Ansible deploy stage after a successful build')
    }

    environment {
        STORAGE_ACCOUNT   = 'CHANGE_ME_storageaccountname'
        STORAGE_CONTAINER = 'naukri-artifacts'
        AZURE_CREDS       = credentials('azure-storage-sas-token')
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

        stage('11. Distribute Artifact') {
            parallel {
                stage('Upload to Storage Account') {
                    steps {
                        echo '===== UPLOADING VERSIONED BLOB TO AZURE STORAGE ACCOUNT ====='
                        powershell '''
                            $installer = Get-ChildItem "$env:WORKSPACE\\dist" -Filter "NaukriAutomator Setup*.exe" | Select-Object -First 1
                            $blobName = "NaukriAutomator-Setup-$env:APP_VERSION.exe"

                            az storage blob upload `
                                --account-name $env:STORAGE_ACCOUNT `
                                --container-name $env:STORAGE_CONTAINER `
                                --name $blobName `
                                --file $installer.FullName `
                                --sas-token $env:AZURE_CREDS
                            if ($LASTEXITCODE -ne 0) {
                                exit $LASTEXITCODE
                            }

                            Write-Host "Uploaded as $blobName (no overwrite -- every version is retained)"
                        '''
                    }
                }
                stage('Copy to Secondary Target VM') {
                    steps {
                        echo '===== COPYING TO SECONDARY TARGET VM ====='
                        powershell '''
                            $installer = Get-ChildItem "$env:WORKSPACE\\dist" -Filter "NaukriAutomator Setup*.exe" | Select-Object -First 1
                            $destName = "NaukriAutomator-Setup-$env:APP_VERSION.exe"
                            Copy-Item $installer.FullName -Destination "\\\\CHANGE_ME_SECONDARY_VM\\share\\artifacts\\$destName" -Force
                        '''
                    }
                }
            }
        }

        stage('12. Deploy via Ansible') {
            when {
                expression { return params.DEPLOY }
            }
            steps {
                echo '===== DEPLOY TO WINDOWS VM VIA ANSIBLE (pulls versioned blob from Storage Account) ====='
                bat """
                    wsl ansible-playbook -i ${params.TARGET_VM_INVENTORY} ansible/install_naukri.yml ^
                        -e app_version=${params.APP_VERSION} ^
                        -e storage_account=%STORAGE_ACCOUNT% ^
                        -e storage_container=%STORAGE_CONTAINER% ^
                        -e sas_token=%AZURE_CREDS%
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
            Artifacts successfully generated, archived and distributed.
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
