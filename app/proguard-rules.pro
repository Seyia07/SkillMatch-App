# SkillMatch release rules.
# The model is parsed with kotlinx.serialization's JsonElement tree (no reflection,
# no @Serializable classes), so no keep rules are needed for it.

# Keep line numbers so crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
