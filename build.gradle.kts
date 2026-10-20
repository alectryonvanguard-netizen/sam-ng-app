
      - name: Inspect Gradle configuration
        run: |
          echo "=== Gradle files ==="
          find . -maxdepth 3 \( -name "settings.gradle.kts" -o -name "build.gradle.kts" -o -name "settings.gradle" -o -name "build.gradle" \) -print

          echo "=== Settings file ==="
          cat settings.gradle.kts

          echo "=== Root build file ==="
          cat build.gradle.kts

          echo "=== App build file ==="
          cat app/build.gradle.kts

          echo "=== Android plugin tasks ==="
          gradle --no-daemon :app:tasks --all
