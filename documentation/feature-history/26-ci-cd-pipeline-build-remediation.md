# CI/CD Pipeline & Build Remediation (`.github/workflows/build-and-test.yml`)

  - Automated GitHub Actions pipeline with `test` stage (unit tests) and `build-apks` stage (debug APKs for Mobile & Wear OS ready for download).
  - Remediation for Issues #20-#22: Restored full localized strings schema across mobile screens (`values/strings.xml` and `values-de/strings.xml`) and fixed unescaped format specifier dollar signs in `WearSettingsLogicTest.kt`. Full pipeline green (`#34879524027`).
