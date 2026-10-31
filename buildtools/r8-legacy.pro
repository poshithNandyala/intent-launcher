# Used only by build.sh, whose R8 (3.3, from bundletool) is older than the Android versions Intent
# targets. Shrinking stays on (unused Kotlin runtime is removed); optimizations that rewrite code
# against the platform library are off, because an older R8 can't know which newer types exist on
# which phones. The Gradle build uses AGP's current R8 and doesn't need this.
-dontoptimize
