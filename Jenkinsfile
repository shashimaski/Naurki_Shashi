pipeline {
    agent any

    stages {
        stage('1. Checkout') {
            steps {
                echo '===== CHECKOUT SOURCE CODE ====='
                git branch: 'main',
                    url: 'https://github.com/shashimaski/Naurki_Shashi.git'
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
                echo '===== INSTALL PLAYWRIGHT CHROMIUM ====='
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

        stage('7b. SonarQube Analysis') {
            steps {
                echo '===== SONARQUBE ANALYSIS ====='
                script {
                    def scannerHome = tool 'SonarScanner'
                    withSonarQubeEnv('SonarQubeServer') {
                        bat "\"${scannerHome}\\bin\\sonar-scanner.bat\""
                    }
                }
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
                archiveArtifacts artifacts: 'dist/*/.exe',
                                  fingerprint: true
            }
        }

        stage('11. Upload to Azure Blob Storage') {
            steps {
                echo '===== UPLOADING TO AZURE BLOB STORAGE ====='
                azureUpload(
                    containerName: 'smcont',
                    storageType: 'blobstorage',
                    filesPath: 'dist/*/.exe',
                    storageCredentialId: 'azure-storage-cred'
                )
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
